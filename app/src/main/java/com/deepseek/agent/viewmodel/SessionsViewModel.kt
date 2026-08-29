package com.deepseek.agent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.agent.data.local.SessionDao
import com.deepseek.agent.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionsViewModel(private val sessionDao: SessionDao) : ViewModel() {

    val sessions: StateFlow<List<SessionEntity>> = sessionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSession(id: String) {
        viewModelScope.launch { sessionDao.delete(id) }
    }

    fun renameSession(id: String, title: String) {
        viewModelScope.launch {
            sessionDao.getById(id)?.let {
                sessionDao.update(it.copy(title = title))
            }
        }
    }
}
