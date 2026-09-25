package com.sahil.octacode.core.runtime.extract

/** Raised when an archive cannot be read safely or faithfully. */
class ExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)
