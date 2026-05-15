package com.ethossoftworks.reaperbleiem.lib

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class PersistentMapSerializer<K, V>(
    private val keySerializer: KSerializer<K>,
    private val valueSerializer: KSerializer<V>,
) : KSerializer<PersistentMap<K, V>> {

    private val delegate = MapSerializer(keySerializer, valueSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: PersistentMap<K, V>) {
        encoder.encodeSerializableValue(delegate, value)
    }

    override fun deserialize(decoder: Decoder): PersistentMap<K, V> {
        val compositeDecoder = decoder.beginStructure(descriptor)
        val builder = persistentMapOf<K, V>().builder()

        while (true) {
            val index = compositeDecoder.decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break

            val key = compositeDecoder.decodeSerializableElement(descriptor, index, keySerializer)
            val nextIndex = compositeDecoder.decodeElementIndex(descriptor)
            val value = compositeDecoder.decodeSerializableElement(descriptor, nextIndex, valueSerializer)

            builder[key] = value
        }

        compositeDecoder.endStructure(descriptor)
        return builder.build()
    }
}
