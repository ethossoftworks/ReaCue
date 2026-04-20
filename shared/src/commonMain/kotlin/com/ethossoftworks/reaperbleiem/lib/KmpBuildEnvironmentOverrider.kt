package com.ethossoftworks.reaperbleiem.lib

import com.outsidesource.kmpbuild.IKmpBuildEnvironmentOverrider
import com.outsidesource.kmpbuild.KmpBuildEnvironment
import com.outsidesource.oskitkmp.outcome.unwrapOrNull
import com.outsidesource.oskitkmp.storage.IKmpKvStore
import com.outsidesource.oskitkmp.storage.IKmpKvStoreNode
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update

class KmpBuildEnvironmentOverrider(private val store: IKmpKvStore) : IKmpBuildEnvironmentOverrider {
    private val environmentKey = "environment"
    private val node = atomic<IKmpKvStoreNode?>(null)
    private val environment = atomic<KmpBuildEnvironment?>(null)

    suspend fun init() {
        node.update { store.openNode("kmp-build-info").unwrapOrNull() }
        val localEnvironment = node.value?.getSerializable(environmentKey, KmpBuildEnvironment.serializer())
        environment.update { localEnvironment }
    }

    override suspend fun setEnvironmentOverride(value: KmpBuildEnvironment) {
        environment.update { value }
        node.value?.putSerializable(environmentKey, value, KmpBuildEnvironment.serializer())
    }

    override fun getEnvironmentOverride(default: KmpBuildEnvironment): KmpBuildEnvironment {
        return environment.value ?: default
    }
}
