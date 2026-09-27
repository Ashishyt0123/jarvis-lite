package com.jarvislite.assistant

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvislite.assistant.databinding.ActivityMemoryBinding

class MemoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.forgetAllButton.setOnClickListener {
            MemoryStore.forgetAll(this)
            Toast.makeText(this, "All memory cleared", Toast.LENGTH_SHORT).show()
            refreshList()
        }

        refreshList()
    }

    private fun refreshList() {
        binding.factsContainer.removeAllViews()
        val facts = MemoryStore.getAll(this)

        if (facts.length() == 0) {
            val empty = TextView(this).apply {
                text = "Nothing remembered yet. Try saying: \"remember that my mom's number is saved as Mummy\""
                setPadding(0, 24, 0, 0)
            }
            binding.factsContainer.addView(empty)
            return
        }

        facts.keys().forEach { key ->
            val value = facts.getString(key)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 12)
            }
            val text = TextView(this).apply {
                text = "$key: $value"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val deleteBtn = android.widget.Button(this).apply {
                text = "Forget"
                setOnClickListener {
                    MemoryStore.forget(this@MemoryActivity, key)
                    refreshList()
                }
            }
            row.addView(text)
            row.addView(deleteBtn)
            binding.factsContainer.addView(row)
        }
    }
}
