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

class WearViewModel(application: Application) :
    SharedViewModel(application)
{
    override val TAG: String
        get() = "WearViewModel"
    override val remoteTypeName: String
        get() = "MOBILE"
    override val remoteCapabilityName: String
        get() = "verify_remote_example_mobile_app"

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
        //...
    }

    override fun pushToTalkLocal(on: Boolean) {
        super.pushToTalkLocal(on)
        //...
    }
}
