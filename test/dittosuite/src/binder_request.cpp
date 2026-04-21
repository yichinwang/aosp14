// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#if __ANDROID__

#include <ditto/binder.h>
#include <ditto/binder_request.h>
#include <ditto/logger.h>

namespace dittosuite {

BinderRequest::BinderRequest(const std::string& kName, const Params& params,
                             const std::string& service_name)
    : Instruction(kName, params), service_name_(service_name) {}

BinderRequestDitto::BinderRequestDitto(const Params& params, const std::string& service_name)
    : BinderRequest(kName, params, service_name) {}

void BinderRequestDitto::RunSingle() {
  const char c = 1;

  char ret = service_->sync(c);
  if (ret != (~c)) {
    LOGF("Wrong result, expected: " + std::to_string(~c) + ", but got: " + std::to_string(ret));
  }
  LOGD("Returned from Binder request: " + std::to_string(ret));
}

void BinderRequestDitto::SetUp() {
  LOGD("Starting binder requester for service: " + service_name_);
  service_ = getBinderService<IDittoBinder>(service_name_);
  service_->start();
  Instruction::SetUp();
}

void BinderRequestDitto::TearDownSingle(bool is_last) {
  Instruction::TearDownSingle(is_last);
  if (is_last) {
    LOGD("This is the last, sending termination request");
    service_->end();
  }
}

BinderRequestMountService::BinderRequestMountService(const Params& params)
    : BinderRequest(kName, params, "mount") {}

void BinderRequestMountService::RunSingle() {
  bool ret = service_->isUsbMassStorageConnected();
  LOGD("Returned from Binder request: " + std::to_string(ret));
}

void BinderRequestMountService::SetUp() {
  LOGD("Starting binder requester for service: " + service_name_);
  service_ = getBinderService<android::IMountService>(service_name_);
  Instruction::SetUp();
}

void BinderRequestMountService::TearDownSingle(bool last) {
  Instruction::TearDownSingle(last);
}

}  // namespace dittosuite

#endif
