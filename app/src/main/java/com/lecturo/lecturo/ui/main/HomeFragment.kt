package com.lecturo.lecturo.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.FragmentHomeBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.auth.LoginActivity
import com.lecturo.lecturo.ui.event.EventActivity
import com.lecturo.lecturo.ui.task.TasksActivity
import com.lecturo.lecturo.ui.teaching.TeachingActivity
import com.lecturo.lecturo.viewmodel.main.MainViewModel
import com.lecturo.lecturo.viewmodel.profile.ProfileViewModel
import com.lecturo.lecturo.ui.consultation.ConsultationActivity
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturo.lecturo.ui.components.DetailedDonutChartScreen
import com.lecturo.lecturo.utils.EventCategories

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel by activityViewModels<MainViewModel> {
        ViewModelFactory.getInstance(requireActivity())
    }

    private val profileViewModel by viewModels<ProfileViewModel> {
        ViewModelFactory.getInstance(requireContext())
    }

    private lateinit var agendaAdapter: AgendaAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupSwipeToRefresh()
        setupToggleSwitch()

        if (profileViewModel.currentUser.value == null) {
            profileViewModel.loadUserProfile()
        }

        observeMainViewModel()
        observeProfileViewModel()
        updateDateDisplay()

        binding.composeViewDates.setContent {
            MaterialTheme { WeeklyCalendarRow(viewModel = mainViewModel) }
        }

        binding.composeViewDashboard.setContent {
            MaterialTheme {
                DashboardGridScreen(viewModel = mainViewModel) { routeId ->
                    when (routeId) {
                        0 -> startActivity(Intent(requireContext(), TasksActivity::class.java))
                        1 -> startActivity(Intent(requireContext(), EventActivity::class.java))
                        2 -> startActivity(Intent(requireContext(), TeachingActivity::class.java))
                        3 -> startActivity(Intent(requireContext(), ConsultationActivity::class.java))
                    }
                }
            }
        }

        val bottomAppBar = requireActivity().findViewById<com.google.android.material.bottomappbar.BottomAppBar>(R.id.bottomAppBar)
        val fab = requireActivity().findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab)

        binding.nestedScrollView.setOnScrollChangeListener(androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            if (dy > 10 && fab.isOrWillBeShown) {
                fab.hide()
                bottomAppBar.performHide()
            } else if (dy < -10 && fab.isOrWillBeHidden) {
                fab.show()
                bottomAppBar.performShow()
            }
        })
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            mainViewModel.refreshData()
            profileViewModel.loadUserProfile()
        }
    }

    private fun setupToggleSwitch() {
        binding.toggleViewMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnViewList -> showListView()
                    R.id.btnViewChart -> showChartView()
                }
            }
        }
    }

    private fun showListView() {
        binding.agendaRecyclerView.visibility = View.VISIBLE
        binding.composeViewAgendaChart.visibility = View.GONE
        val currentList = mainViewModel.todaysAgenda.value
        if (currentList.isNullOrEmpty()) {
            binding.agendaRecyclerView.visibility = View.GONE
            binding.emptyAgendaLayout.visibility = View.VISIBLE
        } else {
            binding.emptyAgendaLayout.visibility = View.GONE
        }
    }

    private fun showChartView() {
        binding.agendaRecyclerView.visibility = View.GONE
        binding.composeViewAgendaChart.visibility = View.VISIBLE
        binding.emptyAgendaLayout.visibility = View.GONE
        binding.composeViewAgendaChart.setContent {
            MaterialTheme {
                val agendaData by mainViewModel.todaysAgenda.observeAsState(emptyList())
                DetailedDonutChartScreen(agendaData)
            }
        }
    }

    private fun observeMainViewModel() {
        mainViewModel.getSession().observe(viewLifecycleOwner) { user ->
            if (!user.isLogin) {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                activity?.finish()
            }
        }

        mainViewModel.greetingText.observe(viewLifecycleOwner) { greeting ->
            binding.greetingTextView.text = greeting
        }

        mainViewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            if (!isRefreshing) binding.swipeRefreshLayout.isRefreshing = false
        }

        mainViewModel.todaysAgenda.observe(viewLifecycleOwner) { agenda ->
            if (binding.toggleViewMode.checkedButtonId == R.id.btnViewList) {
                if (agenda.isEmpty()) {
                    binding.agendaRecyclerView.visibility = View.GONE
                    binding.emptyAgendaLayout.visibility = View.VISIBLE
                } else {
                    binding.agendaRecyclerView.visibility = View.VISIBLE
                    binding.emptyAgendaLayout.visibility = View.GONE
                }
            }
            agendaAdapter.submitList(agenda)
        }
    }

    private fun observeProfileViewModel() {
        profileViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                val displayName = if (user.fullName.isNotEmpty()) user.fullName else user.phoneNumber
                if (binding.nameTextView.text.toString() != displayName) binding.nameTextView.text = displayName
                if (!isDetached && context != null) {
                    Glide.with(requireContext()).load(user.photoUrl).placeholder(R.drawable.ic_profile_placeholder).error(R.drawable.ic_profile_placeholder).diskCacheStrategy(DiskCacheStrategy.ALL).into(binding.profileImageView)
                }
            }
        }
    }

    private fun setupRecyclerViews() {
        agendaAdapter = AgendaAdapter { calendarEntry ->
            when (calendarEntry.sourceFeatureType) {
                "TEACHING_SCHEDULE" -> startActivity(Intent(requireContext(), TeachingActivity::class.java)) // 🔴 PERBAIKAN: Kunci routing baru
                "EVENT" -> startActivity(Intent(requireContext(), EventActivity::class.java))
                "TASK" -> startActivity(Intent(requireContext(), TasksActivity::class.java))
                "CONSULTATION", "Konsultasi" -> startActivity(Intent(requireContext(), ConsultationActivity::class.java))
            }
        }
        binding.agendaRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = agendaAdapter
        }
    }

    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
        binding.dateTextView.text = dateFormat.format(Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

@Composable
fun WeeklyCalendarRow(viewModel: MainViewModel) {
    val dates by viewModel.calendarDates.observeAsState(emptyList())
    val listState = rememberLazyListState()
    LaunchedEffect(dates) { if (dates.isNotEmpty() && listState.firstVisibleItemIndex == 0) listState.animateScrollToItem(7) }
    LazyRow(
        state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.fillMaxWidth()
    ) {
        items(dates) { dateItem -> DateItemCard(dateItem = dateItem, onClick = { viewModel.loadAgendaForDate(dateItem.date) }) }
    }
}

@Composable
fun DateItemCard(dateItem: com.lecturo.lecturo.viewmodel.main.DateItem, onClick: () -> Unit) {
    val dayFormat = remember { SimpleDateFormat("EEE", Locale("id", "ID")) }
    val dateFormat = remember { SimpleDateFormat("dd", Locale.getDefault()) }
    val dayName = dayFormat.format(dateItem.date).uppercase()
    val dateNumber = dateFormat.format(dateItem.date)
    val isSelected = dateItem.isSelected
    val isToday = dateItem.isToday
    val bgColor = when { isSelected -> colorResource(R.color.colorPrimary) else -> colorResource(R.color.colorPrimaryContainer) }
    val textColor = when { isSelected -> colorResource(R.color.white) else -> colorResource(R.color.text_primary) }
    val cardModifier = Modifier.width(55.dp).height(80.dp).clip(RoundedCornerShape(12.dp)).background(bgColor).clickable(onClick = onClick)
        .then(if (isToday && !isSelected) Modifier.border(2.dp, colorResource(R.color.colorPrimary), RoundedCornerShape(12.dp)) else Modifier).padding(8.dp)
    Column(modifier = cardModifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text = dayName, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = dateNumber, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(4.dp))
        if (dateItem.categories.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                dateItem.categories.map { it.trim().lowercase(Locale.getDefault()) }.distinct().forEach { category ->
                    val dotColor = when {
                        category == "tugas" -> colorResource(R.color.task_color)
                        category == "mengajar" -> colorResource(R.color.teaching_color)
                        category == "konsultasi" -> colorResource(R.color.consultation_color)
                        EventCategories.list.contains(category) -> colorResource(R.color.event_color)
                        else -> Color.Transparent
                    }
                    if (dotColor != Color.Transparent) {
                        Box(modifier = Modifier.size(6.dp).background(color = dotColor, shape = CircleShape).border(width = 1.dp, color = Color.White, shape = CircleShape))
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardGridScreen(viewModel: MainViewModel, onCardClick: (Int) -> Unit) {
    val taskStats by viewModel.taskStats.observeAsState(MainViewModel.StatProgress(0, 0))
    val eventStats by viewModel.eventStats.observeAsState(MainViewModel.StatProgress(0, 0))
    val consultStats by viewModel.consultationStats.observeAsState(MainViewModel.StatProgress(0, 0))

    // 🔴 PERBAIKAN: Menggunakan State Statistik Mengajar Baru
    val teachingStats by viewModel.teachingStats.observeAsState(MainViewModel.StatProgress(0, 0))

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressDashboardCard(title = "Tugas", iconRes = R.drawable.ic_task, colorRes = R.color.task_color, completed = taskStats.completed, total = taskStats.total, modifier = Modifier.weight(1f), onClick = { onCardClick(0) })
            ProgressDashboardCard(title = "Acara", iconRes = R.drawable.ic_event_2, colorRes = R.color.event_color, completed = eventStats.completed, total = eventStats.total, modifier = Modifier.weight(1f), onClick = { onCardClick(1) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 🔴 PERBAIKAN: Ganti InfoDashboardCard menjadi ProgressDashboardCard
            ProgressDashboardCard(title = "Mengajar", iconRes = R.drawable.ic_class, colorRes = R.color.teaching_color, completed = teachingStats.completed, total = teachingStats.total, modifier = Modifier.weight(1f), onClick = { onCardClick(2) })
            ProgressDashboardCard(title = "Konsultasi", iconRes = R.drawable.ic_consultant, colorRes = R.color.consultation_color, completed = consultStats.completed, total = consultStats.total, modifier = Modifier.weight(1f), onClick = { onCardClick(3) })
        }
    }
}

@Composable
fun ProgressDashboardCard(title: String, iconRes: Int, colorRes: Int, completed: Int, total: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
    val percentage = (progress * 100).toInt()
    val mainColor = colorResource(id = colorRes)
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_background)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), modifier = modifier.height(130.dp).clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).background(mainColor.copy(alpha = 0.15f), CircleShape)) {
                    Icon(painterResource(id = iconRes), contentDescription = null, tint = mainColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colorResource(id = R.color.text_primary))
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = if (total == 0) "Kosong" else "Selesai", fontSize = 11.sp, color = colorResource(id = R.color.text_secondary), fontWeight = FontWeight.Medium)
                    Text(text = if (total == 0) "-" else "$completed / $total", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = colorResource(id = R.color.text_primary))
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                    CircularProgressIndicator(progress = { 1f }, color = mainColor.copy(alpha = 0.15f), strokeWidth = 4.dp, modifier = Modifier.fillMaxSize())
                    CircularProgressIndicator(progress = { progress }, color = mainColor, strokeWidth = 4.dp, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, modifier = Modifier.fillMaxSize())
                    Text(text = "$percentage%", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = mainColor)
                }
            }
        }
    }
}