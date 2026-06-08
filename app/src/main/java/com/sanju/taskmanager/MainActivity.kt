package com.sanju.taskmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.sanju.taskmanager.adapter.TaskAdapter
import com.sanju.taskmanager.database.TaskDatabase
import com.sanju.taskmanager.databinding.ActivityMainBinding
import com.sanju.taskmanager.model.Task
import com.sanju.taskmanager.repository.TaskRepository
import com.sanju.taskmanager.viewmodel.TaskViewModel
import com.sanju.taskmanager.viewmodel.TaskViewModelFactory
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var taskViewModel: TaskViewModel

    private var allTasksList = listOf<Task>()
    private var selectedPriority = "All"
    private var selectedCategory = "All Categories"
    private var selectedStatus = "All"
    private var selectedSort = "Newest"

    private val themePrefs by lazy {
        getSharedPreferences("theme_prefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermission()
        setupViewModel()
        setupRecyclerView()
        setupSwipeToDelete()
        observeTasks()
        setupSearch()
        setupPriorityFilter()
        setupCategoryFilter()
        setupStatusFilter()
        setupSortFilter()
        setupThemeToggle()

        binding.btnAddTask.setOnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }
    }

    private fun applySavedTheme() {
        val isDarkMode = themePrefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun setupThemeToggle() {
        updateThemeButtonText()

        binding.btnToggleTheme.setOnClickListener {
            val newDarkMode = !themePrefs.getBoolean("dark_mode", false)

            themePrefs.edit()
                .putBoolean("dark_mode", newDarkMode)
                .apply()

            AppCompatDelegate.setDefaultNightMode(
                if (newDarkMode) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun updateThemeButtonText() {
        val isDarkMode = themePrefs.getBoolean("dark_mode", false)
        binding.btnToggleTheme.text = if (isDarkMode) "Light" else "Dark"
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }

    private fun setupViewModel() {
        val taskDao = TaskDatabase.getDatabase(applicationContext).taskDao()
        val repository = TaskRepository(taskDao)
        val factory = TaskViewModelFactory(repository)

        taskViewModel = ViewModelProvider(this, factory)[TaskViewModel::class.java]
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onEditClick = { task -> openEditTaskScreen(task) },
            onDeleteClick = { task -> deleteTaskWithUndo(task) },
            onCompletedClick = { task -> toggleTaskCompleted(task) }
        )

        binding.rvTasks.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = taskAdapter
        }
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(
                viewHolder: RecyclerView.ViewHolder,
                direction: Int
            ) {
                val position = viewHolder.bindingAdapterPosition
                val currentList = taskAdapter.getCurrentList()

                if (position != RecyclerView.NO_POSITION && position < currentList.size) {
                    deleteTaskWithUndo(currentList[position])
                }
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.rvTasks)
    }

    private fun observeTasks() {
        taskViewModel.allTasks.observe(this) { tasks ->
            allTasksList = tasks
            updateStatistics(tasks)
            applyFilters()
        }
    }

    private fun updateStatistics(tasks: List<Task>) {
        val totalTasks = tasks.size
        val completedTasks = tasks.count { it.isCompleted }
        val pendingTasks = totalTasks - completedTasks

        val completionRate = if (totalTasks == 0) {
            0
        } else {
            (completedTasks * 100) / totalTasks
        }

        binding.tvTotalTasks.text =
            getString(R.string.total_tasks, totalTasks)

        binding.tvCompletedTasks.text =
            getString(R.string.completed_tasks, completedTasks)

        binding.tvPendingTasks.text =
            getString(R.string.pending_tasks, pendingTasks)

        binding.tvCompletionRate.text =
            getString(R.string.completion_rate, completionRate)

        binding.tvProgressPercentage.text =
            getString(R.string.progress_percentage, completionRate)
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                applyFilters()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupPriorityFilter() {
        binding.chipGroupPriority.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedPriority = when (checkedIds.firstOrNull()) {
                binding.chipHigh.id -> "High"
                binding.chipMedium.id -> "Medium"
                binding.chipLow.id -> "Low"
                else -> "All"
            }

            applyFilters()
        }
    }

    private fun setupCategoryFilter() {
        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedCategory = when (checkedIds.firstOrNull()) {
                binding.chipWork.id -> "Work"
                binding.chipPersonal.id -> "Personal"
                binding.chipStudy.id -> "Study"
                binding.chipShopping.id -> "Shopping"
                else -> "All Categories"
            }

            applyFilters()
        }
    }

    private fun setupStatusFilter() {
        binding.chipGroupStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedStatus = when (checkedIds.firstOrNull()) {
                binding.chipPending.id -> "Pending"
                binding.chipCompleted.id -> "Completed"
                else -> "All"
            }

            applyFilters()
        }
    }

    private fun setupSortFilter() {
        binding.chipGroupSort.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedSort = when (checkedIds.firstOrNull()) {
                binding.chipOldest.id -> "Oldest"
                binding.chipDueDate.id -> "Due Date"
                else -> "Newest"
            }

            applyFilters()
        }
    }

    private fun applyFilters() {
        val query = binding.etSearch.text.toString().trim()

        val filteredList = allTasksList.filter { task ->
            val matchesSearch = query.isBlank() ||
                    task.title.contains(query, ignoreCase = true) ||
                    task.description.contains(query, ignoreCase = true) ||
                    task.priority.contains(query, ignoreCase = true) ||
                    task.category.contains(query, ignoreCase = true) ||
                    task.dueDate.contains(query, ignoreCase = true)

            val matchesPriority = selectedPriority == "All" ||
                    task.priority.equals(selectedPriority, ignoreCase = true)

            val matchesCategory = selectedCategory == "All Categories" ||
                    task.category.equals(selectedCategory, ignoreCase = true)

            val matchesStatus = when (selectedStatus) {
                "Pending" -> !task.isCompleted
                "Completed" -> task.isCompleted
                else -> true
            }

            matchesSearch && matchesPriority && matchesCategory && matchesStatus
        }

        val sortedList = sortTasks(filteredList)

        taskAdapter.submitList(sortedList)
        updateEmptyState(sortedList, query)
    }

    private fun sortTasks(tasks: List<Task>): List<Task> {
        return when (selectedSort) {
            "Oldest" -> tasks.sortedBy { it.id }
            "Due Date" -> tasks.sortedBy { parseDueDateMillis(it.dueDate) }
            else -> tasks.sortedByDescending { it.id }
        }
    }

    private fun parseDueDateMillis(dueDate: String): Long {
        return try {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            dateFormat.parse(dueDate)?.time ?: Long.MAX_VALUE
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun updateEmptyState(filteredList: List<Task>, query: String) {
        if (filteredList.isEmpty()) {
            binding.tvEmptyState.visibility = android.view.View.VISIBLE
            binding.rvTasks.visibility = android.view.View.GONE

            binding.tvEmptyState.text = when {
                allTasksList.isEmpty() -> {
                    "No tasks yet\nTap Add Task to create your first task"
                }

                query.isNotBlank() -> {
                    "No matching tasks found"
                }

                selectedStatus != "All" -> {
                    "No $selectedStatus tasks found"
                }

                selectedCategory != "All Categories" -> {
                    "No $selectedCategory category tasks found"
                }

                selectedPriority != "All" -> {
                    "No $selectedPriority priority tasks found"
                }

                else -> {
                    "No tasks found"
                }
            }
        } else {
            binding.tvEmptyState.visibility = android.view.View.GONE
            binding.rvTasks.visibility = android.view.View.VISIBLE
        }
    }

    private fun openEditTaskScreen(task: Task) {
        val intent = Intent(this, AddTaskActivity::class.java).apply {
            putExtra("task_id", task.id)
            putExtra("task_title", task.title)
            putExtra("task_description", task.description)
            putExtra("task_priority", task.priority)
            putExtra("task_category", task.category)
            putExtra("task_due_date", task.dueDate)
            putExtra("task_is_completed", task.isCompleted)
        }

        startActivity(intent)
    }

    private fun deleteTaskWithUndo(task: Task) {
        taskViewModel.deleteTask(task)

        Snackbar.make(binding.root, "Task deleted", Snackbar.LENGTH_LONG)
            .setAction("UNDO") {
                taskViewModel.insertTask(task)
            }
            .show()
    }

    private fun toggleTaskCompleted(task: Task) {
        val updatedTask = task.copy(
            isCompleted = !task.isCompleted
        )

        taskViewModel.updateTask(updatedTask)
    }
}