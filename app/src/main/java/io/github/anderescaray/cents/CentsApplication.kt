package io.github.anderescaray.cents

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Annotating it with [HiltAndroidApp] is what generates the
 * dependency graph and makes injection available to every activity, view model and worker.
 *
 * **Nothing is seeded here.** A fresh install has no accounts and no categories: everything
 * in the app is something the owner typed (`DECISIONS.md` D1, and PRD §4.1 design principle
 * 3 for the categories). The movement form leads to creating an account when there are none,
 * which is what makes an empty database a first run rather than a dead end.
 */
@HiltAndroidApp
class CentsApplication : Application()
