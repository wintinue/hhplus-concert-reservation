package kr.hhplus.be.server.common.lock

interface DistributedLockExecutor {
    fun <T> execute(key: String, action: () -> T): T
}
