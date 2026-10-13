#include "resident_cp_core.hpp"
#include <cassert>
#include <stdexcept>
struct Fake : lami::CpNativeRuntime {
  int opens=0, resets=0, runs=0, closes=0;
  int fail_at=-1;
  void open() override { ++opens; }
  void clear_kv() override { ++resets; }
  void execute(unsigned p) override {
    if (static_cast<int>(p)==fail_at) throw std::runtime_error("QNN failure");
    ++runs;
  }
  void close() noexcept override { ++closes; }
};
int main() {
  Fake a;
  {
    lami::ResidentCpCore session(a);
    for (int frame=0;frame<3;++frame) {
      session.start_frame();
      for (unsigned p=0;p<16;++p) session.step(p);
    }
    assert(a.opens==1 && a.resets==3 && a.runs==48);
    session.close();
  }
  assert(a.closes==1);
  Fake b; b.fail_at=4;
  {
    lami::ResidentCpCore session(b);
    session.start_frame();
    for (unsigned p=0;p<4;++p) session.step(p);
    bool failed=false;
    try { session.step(4); } catch(const std::runtime_error&) { failed=true; }
    assert(failed);
    failed=false;
    try { session.start_frame(); } catch(const std::logic_error&) { failed=true; }
    assert(failed);
  }
  assert(b.closes==1);
  Fake c;
  {
    lami::ResidentCpCore session(c);
    session.start_frame(); session.step(0); session.cancel_frame();
    session.start_frame();
    for (unsigned p=0;p<16;++p) session.step(p);
    assert(c.opens==1 && c.resets==2);
  }
}
