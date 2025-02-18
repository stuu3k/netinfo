package com.ungifted.netinfo

object ResultsManager {
    private val activeResults = mutableListOf<Triple<String, String, Boolean>>()  // title, content, isActive
    private var resultCounter = mutableMapOf<String, Int>()

    private fun getMinimizedTitle(fullTitle: String, isContinuousPing: Boolean = false): String {
        return when {
            fullTitle.startsWith("Network Scan") -> "SCAN"
            isContinuousPing -> "CPING"
            fullTitle.contains("NSLookup", ignoreCase = true) -> "NS"
            fullTitle.contains("DHCP") -> "DHCP"
            fullTitle.contains("Ping") -> "PING"
            fullTitle.contains("Trace") -> "TRACE"
            fullTitle.contains("DNS") -> "DNS"
            else -> fullTitle
        }
    }

    fun addResult(title: String, content: String, isContinuousPing: Boolean = false) {
        // Get base title without any existing counter
        val baseTitle = title.split(" (#").first()
        val minimizedBase = getMinimizedTitle(baseTitle, isContinuousPing)
        
        // Only add if we don't already have an active result with this base title
        if (!hasActiveResultWithBaseTitle(baseTitle, minimizedBase)) {
            // Increment counter for this type
            val counter = resultCounter.getOrDefault(baseTitle, 0) + 1
            resultCounter[baseTitle] = counter
            
            // Create numbered title
            val numberedTitle = "$baseTitle (#$counter)"
            
            // Add as new result
            activeResults.add(Triple(numberedTitle, content, true))
        }
    }

    fun removeResult(title: String) {
        // Get base title and its minimized version
        val baseTitle = title.split(" (#").first()
        val minimizedBase = getMinimizedTitle(baseTitle)
        
        // Remove any result that matches either the full title or minimized version
        activeResults.removeAll { result -> 
            result.first == title || 
            result.first.startsWith(baseTitle) ||
            result.first.startsWith(minimizedBase)
        }
    }

    fun updateResult(title: String, content: String, isContinuousPing: Boolean = false) {
        // Get base title without any existing counter
        val baseTitle = title.split(" (#").first()
        val minimizedBase = getMinimizedTitle(baseTitle, isContinuousPing)
        
        // Find any active result with this base title or minimized title
        val index = activeResults.indexOfFirst { 
            (it.first.startsWith(baseTitle) || it.first.startsWith(minimizedBase)) && it.third 
        }
        
        if (index != -1) {
            // Update existing result content
            activeResults[index] = activeResults[index].copy(second = content)
        }
    }

    fun getAllResults(): List<Pair<String, String>> = 
        activeResults.filter { it.third }  // Only return active results
            .map { Pair(it.first, it.second) }

    fun resetCounters() {
        resultCounter.clear()
        activeResults.clear()
    }

    fun getCurrentScanTitle(): String? {
        return activeResults.findLast { 
            it.first.startsWith("Network Scan") && it.third 
        }?.first
    }

    private fun hasActiveResultWithBaseTitle(baseTitle: String, minimizedBase: String): Boolean {
        return activeResults.any { 
            (it.first.startsWith(baseTitle) || it.first.startsWith(minimizedBase)) && it.third 
        }
    }

    fun hasResult(title: String): Boolean {
        val baseTitle = title.split(" (#").first()
        val minimizedBase = getMinimizedTitle(baseTitle)
        return hasActiveResultWithBaseTitle(baseTitle, minimizedBase)
    }

    fun getUniqueTitle(baseTitle: String): String {
        val counter = resultCounter.getOrDefault(baseTitle, 0) + 1
        resultCounter[baseTitle] = counter
        return "$baseTitle (#$counter)"
    }
} 