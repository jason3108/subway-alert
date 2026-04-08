package com.subwayalert.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subwayalert.data.repository.LocationTrackRepository
import com.subwayalert.domain.model.LocationTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackUiState(
    val tracks: List<LocationTrack> = emptyList(),
    val expandedTrackId: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class TrackViewModel @Inject constructor(
    private val locationTrackRepository: LocationTrackRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TrackUiState())
    val uiState: StateFlow<TrackUiState> = _uiState.asStateFlow()
    
    init {
        loadTracks()
    }
    
    private fun loadTracks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val tracks = locationTrackRepository.getAllTracks()
                .sortedByDescending { it.startTime }
            _uiState.update { it.copy(tracks = tracks, isLoading = false) }
        }
    }
    
    fun toggleTrackExpand(trackId: String) {
        _uiState.update { state ->
            state.copy(
                expandedTrackId = if (state.expandedTrackId == trackId) null else trackId
            )
        }
    }
    
    fun deleteTrack(trackId: String) {
        viewModelScope.launch {
            locationTrackRepository.deleteTrack(trackId)
            loadTracks()
        }
    }
    
    fun clearAllTracks() {
        viewModelScope.launch {
            locationTrackRepository.clearAllTracks()
            _uiState.update { it.copy(tracks = emptyList(), expandedTrackId = null) }
        }
    }
    
    fun refresh() {
        loadTracks()
    }
}
