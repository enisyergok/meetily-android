package com.meetily.android

import java.io.File
import kotlin.math.sqrt

object Diarizer2 {
    private const val SR = 16000

    fun speakersFor(file: File, segs: List<Pair<Double, Double>>): List<Int> {
        return try {
            val pcm = readPcm(file)
            val raw = segs.map { feats(pcm, it.first, it.second) }
            val norm = normalize(raw)
            val cents = mutableListOf<DoubleArray>()
            val out = mutableListOf<Int>()
            for (f in norm) {
                var best = -1; var bd = 1e9
                for (i in cents.indices) { val d = dist(f, cents[i]); if (d < bd) { bd = d; best = i } }
                if (best >= 0 && bd < 0.6) out.add(best) else { cents.add(f); out.add(cents.size - 1) }
            }
            out
        } catch (e: Exception) { List(segs.size) { 0 } }
    }

    private fun readPcm(f: File): ShortArray {
        val b = f.readBytes()
        val n = (b.size - 44) / 2
        val p = ShortArray(n.coerceAtLeast(0))
        for (i in 0 until n) {
            val lo = b[44 + 2 * i].toInt() and 0xFF
            val hi = b[44 + 2 * i + 1].toInt()
            p[i] = ((hi shl 8) or lo).toShort()
        }
        return p
    }

    private fun feats(p: ShortArray, s0: Double, e0: Double): DoubleArray {
        val s = (s0 * SR).toInt().coerceIn(0, (p.size - 2).coerceAtLeast(0))
        val e = (e0 * SR).toInt().coerceIn(s + 1, p.size)
        var sum = 0.0; var zc = 0; var n = 0
        for (i in s until e) { sum += p[i].toDouble() * p[i].toDouble(); if (i > s && (p[i] >= 0) != (p[i - 1] >= 0)) zc++; n++ }
        val rms = sqrt(sum / maxOf(n, 1))
        val zcr = zc.toDouble() / maxOf(n, 1)
        val pitches = mutableListOf<Double>()
        var f = s
        while (f + 400 < e) { val pv = pitch(p, f); if (pv > 0) pitches.add(pv); f += 200 }
        val pMean = if (pitches.isEmpty()) 0.0 else pitches.average()
        val pVar = if (pitches.isEmpty()) 0.0 else pitches.map { (it - pMean) * (it - pMean) }.average()
        return doubleArrayOf(pMean, zcr, rms, pVar)
    }

    private fun pitch(p: ShortArray, s: Int): Double {
        var bestLag = 0; var bestV = 0.0
        val n = 320
        for (lag in 50..300 step 2) {
            var num = 0.0; var d1 = 0.0; var d2 = 0.0
            for (i in 0 until n) {
                val a = p[s + i].toDouble(); val b = p[s + i + lag].toDouble()
                num += a * b; d1 += a * a; d2 += b * b
            }
            val v = if (d1 > 0 && d2 > 0) num / sqrt(d1 * d2) else 0.0
            if (v > bestV) { bestV = v; bestLag = lag }
        }
        return if (bestLag > 0 && bestV > 0.3) SR.toDouble() / bestLag else 0.0
    }

    private fun normalize(raw: List<DoubleArray>): List<DoubleArray> {
        if (raw.isEmpty()) return raw
        val dim = raw[0].size
        val mean = DoubleArray(dim); val std = DoubleArray(dim)
        for (d in 0 until dim) {
            val vals = raw.map { it[d] }
            mean[d] = vals.average()
            std[d] = sqrt(vals.map { (it - mean[d]) * (it - mean[d]) }.average()).coerceAtLeast(1e-6)
        }
        return raw.map { f -> DoubleArray(dim) { d -> (f[d] - mean[d]) / std[d] } }
    }

    private fun dist(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) { val d = a[i] - b[i]; s += d * d }
        return sqrt(s)
    }
}
