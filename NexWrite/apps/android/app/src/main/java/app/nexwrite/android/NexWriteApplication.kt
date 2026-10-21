// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android

import android.app.Application
import app.nexwrite.android.data.LocalStore

class NexWriteApplication : Application() {
    val store by lazy { LocalStore(this) }
}
