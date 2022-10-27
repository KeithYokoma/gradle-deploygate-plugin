package com.deploygate.gradle.plugins.dsl

import com.deploygate.gradle.plugins.dsl.syntax.DistributionSyntax
import org.gradle.util.Configurable

import javax.annotation.Nonnull
import javax.annotation.Nullable

class Distribution implements DistributionSyntax {

    @Nullable
    String key

    @Nullable
    String releaseNote

    boolean isPresent() {
        return key || releaseNote
    }

    void merge(@Nonnull Distribution other) {
        this.key = this.key ?: other.key
        this.releaseNote = this.releaseNote ?: other.releaseNote
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        Distribution that = (Distribution) o

        if (key != that.key) return false
        if (releaseNote != that.releaseNote) return false

        return true
    }

    int hashCode() {
        int result
        result = (key != null ? key.hashCode() : 0)
        result = 31 * result + (releaseNote != null ? releaseNote.hashCode() : 0)
        return result
    }
}
