package com.swooby.alfredai

import android.app.Application
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.swooby.alfredai.Utils.playAudioResourceOnce

class WearViewModel(application: Application) :
    SharedViewModel(application)
{
    override val TAG: String
        get() = "WearViewModel"
    override val remoteTypeName: String
        get() = "MOBILE"
    override val remoteCapabilityName: String
        get() = "verify_remote_alfredai_mobile_app"

    private val dataClient = Wearable.getDataClient(application)
    private val dataClientListener = DataClient.OnDataChangedListener {
        onDataClientDataChanged(it)
    }

    override fun init() {
        super.init()
        dataClient.addListener(dataClientListener)
    }

    override fun close() {
        super.close()
        dataClient.removeListener(dataClientListener)
    }

    private fun onDataClientDataChanged(dataEvents: DataEventBuffer) {
        for (dataEvent in dataEvents) {
            // Handle data item changes
            Log.d(TAG, "onDataChanged: $dataEvent")
        }
    }

    fun putDataItem(): Task<DataItem> {
        val putDataReq: PutDataRequest = PutDataMapRequest.create("/my_data_path").run {
            dataMap.putString("key", "value")
            asPutDataRequest().setUrgent() // optional
        }
        return dataClient.putDataItem(putDataReq)
            .addOnSuccessListener { dataItem ->
                Log.d(TAG, "putDataItem: Data item set: $dataItem")
            }
    }

    override fun pushToTalk(on: Boolean, sourceNodeId: String?) {
        Log.i(TAG, "pushToTalk(on=$on)")
        if (on) {
            if (pushToTalkState.value != PttState.Pressed) {
                setPushToTalkState(PttState.Pressed)
                playAudioResourceOnce(getApplication(), R.raw.quindar_nasa_apollo_intro)
                //provideHapticFeedback(context)
            }
        } else {
            if (pushToTalkState.value != PttState.Idle) {
                setPushToTalkState(PttState.Idle)
                playAudioResourceOnce(getApplication(), R.raw.quindar_nasa_apollo_outro)
                //provideHapticFeedback(context)
            }
        }

        var doPushToTalkLocal = true

        if (sourceNodeId == null) {
            // request from local/wear
            Log.d(TAG, "pushToTalk: PTT $on **from** local/wear...")
            val remoteAppNodeId = remoteAppNodeId.value
            if (remoteAppNodeId != null) {
                doPushToTalkLocal = false
                // tell remote/mobile app to do the PTTing...
                sendPushToTalkCommand(remoteAppNodeId, on)
            }
        } else {
            // request from remote/mobile
            //_remoteAppNodeId.value = sourceNodeId
            Log.d(TAG, "pushToTalk: PTT $on **from** remote/mobile...")
            doPushToTalkLocal = false
        }

        if (doPushToTalkLocal) {
            pushToTalkLocal(on)
        }
    }

    override fun pushToTalkLocal(on: Boolean) {
        super.pushToTalkLocal(on)
        if (on) {
            TODO("Not yet implemented")
        } else {
            TODO("Not yet implemented")
        }
    }
}
