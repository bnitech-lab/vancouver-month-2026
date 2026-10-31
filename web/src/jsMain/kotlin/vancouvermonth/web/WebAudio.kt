package vancouvermonth.web

import kotlin.js.Promise
import org.khronos.webgl.Float32Array
import org.w3c.dom.events.EventTarget

// Kotlin declarations for the parts of the browser's Web Audio API the sound uses, and nothing more: Kotlin's DOM
// declarations leave Web Audio out. Every sound in the game is made from these in code, with no audio files.

/** Where sounds are made and timed: the page's [AudioContext], or an [OfflineAudioContext] that renders to a buffer. */
internal external open class BaseAudioContext : EventTarget {
    /** Seconds since the context started, the clock every sound is timed on. */
    val currentTime: Double
    val destination: AudioNode
    fun createGain(): GainNode
    fun createOscillator(): OscillatorNode
    fun createBiquadFilter(): BiquadFilterNode
    fun createDelay(maxDelayTime: Double): DelayNode
    fun createDynamicsCompressor(): DynamicsCompressorNode
    fun createBufferSource(): AudioBufferSourceNode
    fun createBuffer(numberOfChannels: Int, length: Int, sampleRate: Double): AudioBuffer
}

/** The sound the player hears. A browser lets it play only after a tap on the page. */
internal external class AudioContext : BaseAudioContext {
    fun resume(): Promise<Unit>
    fun suspend(): Promise<Unit>
}

/** Sound rendered as fast as it can be into a buffer, heard by no one: for tests. */
internal external class OfflineAudioContext(numberOfChannels: Int, length: Int, sampleRate: Double) : BaseAudioContext {
    fun startRendering(): Promise<AudioBuffer>
}

internal external open class AudioNode : EventTarget {
    fun connect(destination: AudioNode): AudioNode
    fun connect(destination: AudioParam)
}

/** A value of a node that can change over time, on the context's clock. */
internal external class AudioParam {
    var value: Double
    fun setValueAtTime(value: Double, startTime: Double): AudioParam
    fun linearRampToValueAtTime(value: Double, endTime: Double): AudioParam
    fun exponentialRampToValueAtTime(value: Double, endTime: Double): AudioParam
    fun setTargetAtTime(target: Double, startTime: Double, timeConstant: Double): AudioParam
    fun cancelScheduledValues(cancelTime: Double): AudioParam
}

internal external class GainNode : AudioNode {
    val gain: AudioParam
}

internal external open class AudioScheduledSourceNode : AudioNode {
    fun start(time: Double)
    fun stop(time: Double)
}

internal external class OscillatorNode : AudioScheduledSourceNode {
    /** "sine", "triangle", "square" or "sawtooth". */
    var type: String
    val frequency: AudioParam
    val detune: AudioParam
}

internal external class AudioBufferSourceNode : AudioScheduledSourceNode {
    var buffer: AudioBuffer?
    var loop: Boolean
}

internal external class AudioBuffer {
    val length: Int
    val numberOfChannels: Int
    fun getChannelData(channel: Int): Float32Array
}

internal external class BiquadFilterNode : AudioNode {
    /** "lowpass", "highpass" and so on. */
    var type: String
    val frequency: AudioParam
    val Q: AudioParam
}

internal external class DelayNode : AudioNode {
    val delayTime: AudioParam
}

internal external class DynamicsCompressorNode : AudioNode {
    val threshold: AudioParam
    val knee: AudioParam
    val ratio: AudioParam
    val attack: AudioParam
    val release: AudioParam
}
