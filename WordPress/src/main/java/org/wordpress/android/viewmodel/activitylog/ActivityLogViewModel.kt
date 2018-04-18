package org.wordpress.android.viewmodel.activitylog

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.os.Bundle
import android.os.Handler
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.WordPress
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.ActivityLogActionBuilder
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.activity.ActivityLogModel
import org.wordpress.android.fluxc.store.ActivityLogStore
import org.wordpress.android.util.AppLog
import javax.inject.Inject

class ActivityLogViewModel @Inject constructor(val dispatcher: Dispatcher, private val activityLogStore: ActivityLogStore) : ViewModel() {
    enum class ActivityLogListStatus {
        CAN_LOAD_MORE,
        DONE,
        ERROR,
        FETCHING,
        LOADING_MORE
    }

    private var isStarted = false

    private val handler = Handler()

    private val _events = MutableLiveData<List<ActivityLogModel>>()
    val events: LiveData<List<ActivityLogModel>>
        get() = _events


    private val _eventListStatus = MutableLiveData<ActivityLogListStatus>()
    val eventListStatus: LiveData<ActivityLogListStatus>
        get() = _eventListStatus

    lateinit var site: SiteModel

    init {
        dispatcher.register(this)
    }

    override fun onCleared() {
        dispatcher.unregister(this)
        super.onCleared()
    }

    fun writeToBundle(outState: Bundle) {
        outState.putSerializable(WordPress.SITE, site)
    }

    fun readFromBundle(savedInstanceState: Bundle) {
        if (isStarted) {
            // This was called due to a config change where the data survived, we don't need to
            // read from the bundle
            return
        }
        site = savedInstanceState.getSerializable(WordPress.SITE) as SiteModel
    }

    fun start() {
        if (isStarted) {
            return
        }

        reloadEvents()
        fetchEvents(false)

        isStarted = true
    }

    private fun reloadEvents() {
        val eventList = activityLogStore.getActivityLogForSite(site)
        _events.postValue(eventList)
    }

    fun pullToRefresh() {
        fetchEvents(false)
    }

    private fun fetchEvents(loadMore: Boolean) {
        if (shouldFetchEvents(loadMore)) {
            val newStatus = if (loadMore) ActivityLogListStatus.LOADING_MORE else ActivityLogListStatus.FETCHING
            _eventListStatus.postValue(newStatus)
            val payload = ActivityLogStore.FetchActivityLogPayload(site, loadMore)
            dispatcher.dispatch(ActivityLogActionBuilder.newFetchActivitiesAction(payload))
        }
    }

    private fun shouldFetchEvents(loadMore: Boolean): Boolean {
        return if (_eventListStatus.value == ActivityLogListStatus.FETCHING ||
                _eventListStatus.value == ActivityLogListStatus.LOADING_MORE) {
            false
        } else if (loadMore) {
            _eventListStatus.value == ActivityLogListStatus.CAN_LOAD_MORE
        } else {
            true
        }
    }

    fun loadMore() {
        fetchEvents(true)
    }

    // Network Callbacks

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    @SuppressWarnings("unused")
    fun onActivityLogFetched(event: ActivityLogStore.OnActivityLogFetched) {
        if (event.isError) {
            AppLog.e(AppLog.T.PLUGINS, "An error occurred while fetching the Activity log events")
            return
        }

        _events.postValue(activityLogStore.getActivityLogForSite(site, false))
        _eventListStatus.postValue(ActivityLogListStatus.CAN_LOAD_MORE)
    }
}