package com.swooby.alfredai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.ViewModel

class AlfredViewModel : ViewModel() {
    enum class AlfredState {
        //Loading,
        Idle,
        Listening,

        //ListeningPaused,
        Thinking,

        //ThinkingPaused,
        Speaking,
        //SpeakingPaused,
        ;

        fun nextValue(): AlfredState {
            return entries[(ordinal + 1) % entries.size]
        }
    }

    private val isStorageInitialized = mutableStateOf(false)

    val isInitialized: Boolean
        get() = isStorageInitialized.value

    val isPaused = mutableStateOf(true)

    val isIdle: Boolean
        get() {
            return alfredState == AlfredState.Idle
        }

    private val _alfredState = mutableStateOf(AlfredState.Idle)
    var alfredState: AlfredState
        get() {
            return _alfredState.value
        }
        set(value) {
            _alfredState.value = value
        }
}

class AlfredViewProvider(private val viewModel: AlfredViewModel) {
    companion object {
        @Composable
        fun vectorResource(id: Int): ImageVector {
            return ImageVector.Companion.vectorResource(id = id)
        }
    }

    /**
     * https://developer.android.com/design/ui/wear/guides/components/buttons#hierarchy
     */
    enum class ButtonEmphasis {
        High,
        Medium,
        Low,
    }

    data class IconInfo(
        val icon: ImageVector,
        val description: String,
        val emphasis: ButtonEmphasis,
        val enabled: Boolean = true,
    )

    data class IconActionInfo(
        val iconInfo: IconInfo,
        val action: () -> Unit,
        val iconExtraInfo: IconInfo? = null,
        val actionLongPress: (() -> Unit)? = null,
    )

    data class AllIconActionInfos(
        val left: IconActionInfo?,
        val center: IconActionInfo,
        val right: IconActionInfo?
    )

    val icons: AllIconActionInfos
        @Composable
        get() {
            val left: IconActionInfo?
            val center: IconActionInfo
            val right: IconActionInfo?
            val centerLongPressAction = {
                // TODO: Remove; For debug/mock reasons only
                viewModel.alfredState = viewModel.alfredState.nextValue()
                viewModel.isPaused.value = viewModel.isIdle
            }
            when (viewModel.alfredState) {
                //AlfredViewModel.AlfredState.Loading,
                AlfredViewModel.AlfredState.Idle -> {
                    left = IconActionInfo(
                        IconInfo(
                            vectorResource(R.drawable.outline_question_mark_24),
                            "tbd",
                            ButtonEmphasis.Medium,
                            enabled = false
                        ),
                        {
                            // TODO tbd
                        },
                    )
                    center = IconActionInfo(
                        IconInfo(
                            vectorResource(R.drawable.outline_mic_24),
                            "listen",
                            ButtonEmphasis.High
                        ),
                        {
                            viewModel.alfredState = AlfredViewModel.AlfredState.Listening
                            viewModel.isPaused.value = false
                        },
                        actionLongPress = centerLongPressAction
                    )
                    right = IconActionInfo(
                        IconInfo(
                            vectorResource(R.drawable.outline_settings_24),
                            "settings",
                            ButtonEmphasis.Medium,
                            enabled = false
                        ),
                        {
                            // TODO: Go to Settings...
                        }
                    )
                }

                AlfredViewModel.AlfredState.Listening,
                    //AlfredViewModel.AlfredState.ListeningPaused
                    -> {
                    left = IconActionInfo(
                        IconInfo(
                            vectorResource(R.drawable.outline_restart_alt_24),
                            "start over",
                            ButtonEmphasis.Medium,
                            enabled = false
                        ),
                        {
                            // TODO restart listening
                        }
                    )
                    center = IconActionInfo(
                        IconInfo(
                            vectorResource(R.drawable.outline_hearing_24),
                            "listening",
                            ButtonEmphasis.High
                        ),
                        {
                            viewModel.isPaused.value = !viewModel.isPaused.value
                        },
                        iconExtraInfo = if (viewModel.isPaused.value) {
                            IconInfo(
                                vectorResource(R.drawable.outline_play_arrow_24),
                                "resume",
                                ButtonEmphasis.Low
                            )
                        } else {
                            IconInfo(
                                vectorResource(R.drawable.outline_pause_24),
                                "pause",
                                ButtonEmphasis.Low
                            )
                        },
                        actionLongPress = centerLongPressAction
                    )
                    right = IconActionInfo(
                        IconInfo(
                            vectorResource(R.drawable.outline_close_24),
                            "cancel",
                            ButtonEmphasis.Medium
                        ),
                        {
                            viewModel.alfredState = AlfredViewModel.AlfredState.Idle
                            viewModel.isPaused.value = true
                        }
                    )
                }

                AlfredViewModel.AlfredState.Thinking,
                    //AlfredViewModel.AlfredState.ThinkingPaused
                    -> {
                    left = IconActionInfo(
                        IconInfo(
                            vectorResource(R.drawable.outline_arrow_back_24),
                            "back",
                            ButtonEmphasis.High
                        ),
                        {
                            viewModel.alfredState = AlfredViewModel.AlfredState.Listening
                        }
                    )
                    center = IconActionInfo(
                        IconInfo(
                            vectorResource(R.drawable.outline_mindfulness_24),
                            "thinking",
                            ButtonEmphasis.Medium
                        ),
                        {
                            // TODO make nullable to make button non-clickable
                        },
                        actionLongPress = centerLongPressAction
                    )
                    right = IconActionInfo(
                        IconInfo(
                            vectorResource(R.drawable.outline_close_24),
                            "cancel",
                            ButtonEmphasis.Medium
                        ),
                        {
                            viewModel.alfredState = AlfredViewModel.AlfredState.Idle
                            viewModel.isPaused.value = true
                        }
                    )
                }

                AlfredViewModel.AlfredState.Speaking,
                    //AlfredViewModel.AlfredState.SpeakingPaused
                    -> {
                    left = IconActionInfo(
                        IconInfo(
                            vectorResource(R.drawable.outline_replay_5_24),
                            "replay 5",
                            ButtonEmphasis.Medium,
                            enabled = false
                        ),
                        {
                            // TODO replay 5 seconds
                        }
                    )
                    center = IconActionInfo(
                        IconInfo(
                            vectorResource(R.drawable.outline_record_voice_over_24),
                            "speaking",
                            ButtonEmphasis.High
                        ),
                        {
                            viewModel.isPaused.value = !viewModel.isPaused.value
                        },
                        iconExtraInfo = if (viewModel.isPaused.value) {
                            IconInfo(
                                vectorResource(R.drawable.outline_play_arrow_24),
                                "resume",
                                ButtonEmphasis.Low
                            )
                        } else {
                            IconInfo(
                                vectorResource(R.drawable.outline_pause_24),
                                "pause",
                                ButtonEmphasis.Low
                            )
                        },
                        actionLongPress = centerLongPressAction
                    )
                    right = IconActionInfo(
                        IconInfo(
                            vectorResource(R.drawable.outline_close_24),
                            "cancel",
                            ButtonEmphasis.Medium
                        ),
                        {
                            viewModel.alfredState = AlfredViewModel.AlfredState.Idle
                            viewModel.isPaused.value = true
                        }
                    )
                }
            }
            return AllIconActionInfos(left, center, right)
        }
}