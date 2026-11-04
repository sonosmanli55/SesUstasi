package com.aile.sesustasi

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/** Arka planda, ağ varken ~6 saatte bir yeni sürümü denetler ve kurar. */
class UpdateJob : JobService() {

    companion object {
        private const val ID = 4401

        /** Açıksa işi planlar (zaten planlıysa dokunmaz), kapalıysa iptal eder. */
        fun sync(c: Context) {
            val js = c.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (!Store.autoUpdate(c)) {
                js.cancel(ID)
                return
            }
            if (js.getPendingJob(ID) != null) return
            val info = JobInfo.Builder(ID, ComponentName(c, UpdateJob::class.java))
                .setPeriodic(6L * 60 * 60 * 1000)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            js.schedule(info)
        }
    }

    override fun onStartJob(p: JobParameters): Boolean {
        if (!Store.autoUpdate(this)) return false
        Thread {
            try {
                Updater.backgroundUpdate(this)
            } catch (e: Exception) {
            } finally {
                jobFinished(p, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(p: JobParameters): Boolean = true
}
