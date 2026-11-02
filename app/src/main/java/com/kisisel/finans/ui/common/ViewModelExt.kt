package com.kisisel.finans.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.domain.FinanceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * İş kuralı hatalarını Türkçe mesaja çevirir. Beklenmeyen hatalarda hassas bilgi sızmaması için
 * ayrıntı gösterilmez/loglanmaz. onResult: null = başarılı, aksi halde hata mesajı.
 */
fun ViewModel.launchCatching(
    onResult: (String?) -> Unit = {},
    block: suspend () -> Unit
) {
    viewModelScope.launch {
        val error: String? = try {
            block()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: FinanceException) {
            e.message ?: "İşlem yapılamadı."
        } catch (e: Exception) {
            "Beklenmeyen bir hata oluştu."
        }
        onResult(error)
    }
}
