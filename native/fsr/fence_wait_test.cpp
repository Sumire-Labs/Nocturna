// SPDX-License-Identifier: LGPL-3.0-or-later
#include "fence_wait.h"
#include <iostream>

namespace {
struct Fence {
    std::uint64_t completed = 0;
    HRESULT result = S_OK;
    int eventCalls = 0;
    std::uint64_t GetCompletedValue() { return completed; }
    HRESULT SetEventOnCompletion(std::uint64_t, HANDLE) { ++eventCalls; return result; }
};
struct Device {
    HRESULT result = S_OK;
    int probes = 0;
    HRESULT GetDeviceRemovedReason() { ++probes; return result; }
};
DWORD waitResult = WAIT_OBJECT_0;
int waits = 0;
DWORD WINAPI fakeWait(HANDLE, DWORD timeout) {
    if (timeout != 30000) throw std::runtime_error("Unexpected wait timeout");
    ++waits; return waitResult;
}
void require(bool ok) { if (!ok) throw std::runtime_error("Fence protocol assertion failed"); }
template<class Operation> void rejects(Operation operation) {
    bool rejected = false;
    try { operation(); } catch (const std::runtime_error&) { rejected = true; }
    require(rejected);
}
}
int main() {
    try {
        Fence fence; Device device;
        auto wait = [&] { nocturna::waitForFence(fence, device, nullptr, 7, fakeWait); };
        nocturna::waitForFence(fence, device, nullptr, 0, fakeWait);
        require(waits == 0 && fence.eventCalls == 0);
        fence.completed = 7; wait(); require(waits == 0 && fence.eventCalls == 0);
        fence.completed = UINT64_MAX;
        rejects(wait); require(device.probes == 1 && waits == 0 && fence.eventCalls == 0);
        device.result = E_FAIL; rejects(wait); require(device.probes == 2 && waits == 0);
        fence.completed = 0; device.result = S_OK;
        wait(); require(waits == 1 && fence.eventCalls == 1 && device.probes == 3);
        fence.result = E_FAIL; rejects(wait); require(waits == 1);
        fence.result = S_OK; waitResult = WAIT_TIMEOUT; rejects(wait); require(waits == 2 && device.probes == 3);
        waitResult = WAIT_OBJECT_0; device.result = E_FAIL; rejects(wait); require(waits == 3 && device.probes == 4);
        std::cout << "PASS fence protocol: zero/completed/pending, device-loss sentinel, event failure, timeout, loss after wait\n";
        return 0;
    } catch (const std::exception& error) { std::cerr << error.what() << '\n'; return 1; }
}
