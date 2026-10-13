#pragma once
#include <array>
#include <cstdint>
#include <stdexcept>
#include <utility>

namespace lami {
struct CpNativeRuntime {
  virtual ~CpNativeRuntime() = default;
  virtual void open() = 0;
  virtual void clear_kv() = 0;
  virtual void execute(unsigned position) = 0;
  virtual void close() noexcept = 0;
};

// One native graph lifetime per session. No QNN SDK is assumed in this host test.
class ResidentCpCore {
  CpNativeRuntime& runtime_;
  bool loaded_ = false;
  bool poisoned_ = false;
  bool closed_ = false;
  unsigned next_ = 16;
 public:
  explicit ResidentCpCore(CpNativeRuntime& runtime): runtime_(runtime) {}
  ResidentCpCore(const ResidentCpCore&) = delete;
  ResidentCpCore& operator=(const ResidentCpCore&) = delete;
  ~ResidentCpCore() { close(); }
  void start_frame() {
    if (closed_ || poisoned_ || next_ != 16) throw std::logic_error("invalid frame state");
    try {
      if (!loaded_) { runtime_.open(); loaded_ = true; }
      runtime_.clear_kv();
      next_ = 0;
    } catch (...) { poisoned_ = true; next_ = 16; throw; }
  }
  void step(unsigned position) {
    if (closed_ || poisoned_ || !loaded_ || next_ >= 16 || position != next_)
      throw std::logic_error("invalid CP position");
    try { runtime_.execute(position); ++next_; }
    catch (...) { poisoned_ = true; next_ = 16; throw; }
  }
  void cancel_frame() {
    if (closed_) throw std::logic_error("closed");
    next_ = 16;
  }
  void close() noexcept {
    if (!closed_) {
      closed_ = true;
      if (loaded_) runtime_.close();
    }
  }
};
}
