#include <iostream>
#include <sstream>
#include "jvmti.h"
#include <fcntl.h>
#include <unistd.h>

#include <mutex>
#include <atomic>
#include <unordered_map>
 
static std::once_flag flag;
// static int g_logFd = 0;

static int LogFd() {
  // std::call_once(flag, [](){g_logFd = open("ecounter-agent.log", O_CREAT|O_RDWR, 0);});
  return open("ecounter-agent.log", O_CREAT|O_APPEND|O_RDWR, S_IWUSR|S_IRUSR);
  // return g_logFd;
}

static void LogWrite(const std::ostream& o) {
  int fd = LogFd();
  auto& os = static_cast<const std::ostringstream&>(o);
  write(fd, os.str().c_str(), os.str().size() * sizeof(*(os.str().c_str())));
  fsync(fd);
  close(fd);
}

#define log(x)  LogWrite(std::ostringstream() << x << std::endl);

static bool check_jvmti_error(jvmtiEnv *jvmti
  , jvmtiError errnum, const char *str) {
  if ( errnum != JVMTI_ERROR_NONE ) {
      char       *errnum_str;

      errnum_str = NULL;
      //(void)(*jvmti).GetErrorName(jvmti, errnum, &errnum_str);  //the error that function does not take 3 arguments
      (void)(*jvmti).GetErrorName(errnum,&errnum_str);

      log("ERROR: JVMTI: " << errnum << "(" << (errnum_str==NULL?"Unknown":errnum_str) << "):" << (str==NULL?"":str));
      return true;
  }
  return false;
}

class GlobalExceptionCounters {
public:
  void AddExceptionTime(const std::chrono::nanoseconds& ms) {
    std::atomic_fetch_add(&totalExceptionTime_, ms.count());
  }

  void IncrementExceptionCounter() {
    ++ecount_;
  }

  void IncrementCaughtCounter() {
    ++caughtCount_;
  }

  std::atomic<long long> totalExceptionTime_;
  std::atomic<long long> ecount_;
  std::atomic<long long> caughtCount_;
};

class JThreadExceptionCounters {
public:
  explicit JThreadExceptionCounters(GlobalExceptionCounters* counters = nullptr)
    : edepth_(0)
    , counters_(counters) {
  }
  
  void ExceptionThrown() {
    ++edepth_;
    if (counters_)
      counters_->IncrementExceptionCounter();
    if (edepth_ == 1) {
      throwTime_ = std::chrono::high_resolution_clock::now();
    }
  }
  
  void ExceptionCaught() {
    --edepth_;
    if (edepth_ == 0) {
      auto exceptionTimeSpent = std::chrono::high_resolution_clock::now() - throwTime_;
      if (counters_)
        counters_->AddExceptionTime(exceptionTimeSpent);
    }

    if (counters_)
      counters_->IncrementCaughtCounter();
  }

private:
  std::size_t edepth_;
  std::chrono::high_resolution_clock::time_point throwTime_;
  GlobalExceptionCounters* counters_;
};

class DataCollector {
public:
  JThreadExceptionCounters& GetThreadState(jthread jt) {
    std::lock_guard<std::mutex> lock(mx_);
    auto i = threadStates_.insert(TSContainer::value_type(jt, JThreadExceptionCounters(&gec_)));
    return i.first->second;
  }

  void Dump() {
    std::lock_guard<std::mutex> lock(mx_);
    log("Exception count : " << gec_.ecount_.load() << ", exception time : " << gec_.totalExceptionTime_.load()
      << ", number of thread involved : " << threadStates_.size()
      << ", number of caught exceptions : " << gec_.caughtCount_.load());
  }

  typedef std::unordered_map<jthread, JThreadExceptionCounters> TSContainer;
  TSContainer threadStates_;
  GlobalExceptionCounters gec_;
  std::mutex mx_;
};

static DataCollector gDC;

static void JNICALL callbackExceptionThrow(jvmtiEnv *jvmti_env, JNIEnv* env, jthread thr
  , jmethodID method, jlocation location, jobject exception, jmethodID catch_method
  , jlocation catch_location) {
  gDC.GetThreadState(thr).ExceptionThrown();
}


static void JNICALL callbackExceptionCatch(jvmtiEnv *jvmti_env,
            JNIEnv* jni_env,
            jthread thread,
            jmethodID method,
            jlocation location,
            jobject exception) {
  gDC.GetThreadState(thread).ExceptionCaught();
}

extern "C" {

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  log("ecounter agent load called");

  jvmtiEnv *jvmti = 0;
  jint res = (*jvm).GetEnv((void **) &jvmti, JVMTI_VERSION_1_0);
  
  if (res != JNI_OK || jvmti == NULL) {
    // This means that the VM was unable to obtain this version of the
    //   JVMTI interface, this is a fatal error.
     
    log("ERROR: Unable to access JVMTI. " << JVMTI_VERSION_1_0
            << " is your J2SE a 1.5 or newer version? JNIEnv's GetEnv() returned "
            << res);
    return -1;
  }

  jvmtiCapabilities capa;
  (void) memset(&capa, 0, sizeof(jvmtiCapabilities));
  // capa.can_signal_thread = 1;
  // capa.can_get_owned_monitor_info = 1;
  // capa.can_generate_method_entry_events = 1;
  // capa.can_generate_vm_object_alloc_events = 1;
  // capa.can_tag_objects = 1; 
  // capa.can_access_local_variables = 1;//ÄÜ¹»»ñµÃ±¾µØ±äÁ¿
  // capa.can_generate_method_entry_events = 1;//ÄÜ¹»Ê¹ÓÃenrty method¿ª¹Ø
  // capa.can_signal_thread = 1;//ÄÜ¹»interrupt or stop thread
  // capa.can_suspend = 1;

  capa.can_generate_exception_events = 1;
  jvmtiError error = (*jvmti).AddCapabilities(&capa);
  if(error == JVMTI_ERROR_NOT_AVAILABLE) {
      log("error!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
      return -2;
  }
  
  error = (*jvmti).SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_EXCEPTION, 0);
  if (check_jvmti_error(jvmti, error, "Cannot enable exception event"))
    return -3;

  error = (*jvmti).SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_EXCEPTION_CATCH, 0);
  if (check_jvmti_error(jvmti, error, "Cannot enable exception catch event"))
    return -4;


  jvmtiEventCallbacks callbacks;
  (void)memset(&callbacks, 0, sizeof(callbacks));
  callbacks.Exception = &callbackExceptionThrow;// JVMTI_EVENT_EXCEPTION 
  callbacks.ExceptionCatch = &callbackExceptionCatch;

  error = (*jvmti).SetEventCallbacks(&callbacks, (jint)sizeof(callbacks));

  if (check_jvmti_error(jvmti, error, "Cannot set jvmti callbacks"))
    return -5;

  log ("returning from agent load function")
  return JNI_OK;
}


JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm){
  gDC.Dump();
}
}
