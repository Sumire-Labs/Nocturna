// SPDX-License-Identifier: LGPL-3.0-or-later
#pragma once
#include <windows.h>
#include <cstdint>
#include <limits>
#include <stdexcept>
#include <string>

namespace nocturna {
inline void requireSuccess(HRESULT code, const char* operation) {
    if (FAILED(code)) throw std::runtime_error(std::string(operation) + " HRESULT=" + std::to_string(static_cast<unsigned>(code)));
}

// Duck-typed fence/device arguments let failure paths be checked without removing the user's GPU device.
template<class Fence, class Device>
void waitForFence(Fence& fence, Device& device, HANDLE event, std::uint64_t value,
                  decltype(&WaitForSingleObject) wait = &WaitForSingleObject) {
    if (!value) return;
    const auto completed = fence.GetCompletedValue();
    if (completed == std::numeric_limits<std::uint64_t>::max()) {
        requireSuccess(device.GetDeviceRemovedReason(), "FSR device removed");
        throw std::runtime_error("FSR fence reported device removal");
    }
    if (completed >= value) return;
    requireSuccess(fence.SetEventOnCompletion(value, event), "SetEventOnCompletion");
    if (wait(event, 30000) != WAIT_OBJECT_0) throw std::runtime_error("Timed out waiting for FSR GPU work");
    requireSuccess(device.GetDeviceRemovedReason(), "FSR device removed");
}
}
