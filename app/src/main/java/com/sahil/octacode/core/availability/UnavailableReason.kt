package com.sahil.octacode.core.availability

enum class UnavailableReason(val message: String) {
    NOT_IMPLEMENTED("This capability is not implemented yet."),
    NOT_CONFIGURED("Configure a provider or project before using this capability."),
    TOOLCHAIN_MISSING("The required local toolchain is not available on this device."),
    NO_PROJECT_SELECTED("Select a project to make this capability available."),
}
