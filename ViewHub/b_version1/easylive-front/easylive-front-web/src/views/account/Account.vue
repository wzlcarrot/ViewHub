<template>
  <div>
    <Dialog
      :show="loginStore.showLogin"
      :buttons="dialogConfig.buttons"
      width="420px"
      :showCancel="false"
      @close="closeDialog"
      :padding="0"
      :draggable="true"
      :top="100"
      :closeOnClickModal="true"
    >
      <div class="dialog-panel">
        <div class="tab-panel">
          <div 
            :class="['tab-item', opType === 1 ? 'active' : '']" 
            @click="showPanel(1)"
          >
            登录
          </div>
          <div 
            :class="['tab-item', opType === 0 ? 'active' : '']" 
            @click="showPanel(0)"
          >
            注册
          </div>
        </div>
        
        <el-form
          class="login-register"
          :model="formData"
          :rules="rules"
          ref="formDataRef"
        >
          <el-form-item prop="email">
            <el-input
              size="large"
              clearable
              placeholder="请输入邮箱"
              v-model="formData.email"
              maxLength="150"
              @keyup.enter="doSubmit"
            >
              <template #prefix>
                <span class="iconfont icon-account"></span>
              </template>
            </el-input>
          </el-form-item>
          
          <el-form-item prop="password" v-if="opType === 1">
            <el-input
              show-password
              size="large"
              placeholder="请输入密码"
              v-model="formData.password"
              @keyup.enter="doSubmit"
            >
              <template #prefix>
                <span class="iconfont icon-password"></span>
              </template>
            </el-input>
          </el-form-item>
          
          <transition name="fade">
            <div v-if="opType === 0" class="register-form">
              <el-form-item prop="nickName">
                <el-input
                  size="large"
                  clearable
                  placeholder="请输入昵称"
                  v-model="formData.nickName"
                  maxLength="20"
                >
                  <template #prefix>
                    <span class="iconfont icon-account"></span>
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item prop="registerPassword">
                <el-input
                  show-password
                  type="password"
                  size="large"
                  placeholder="请输入密码"
                  v-model="formData.registerPassword"
                >
                  <template #prefix>
                    <span class="iconfont icon-password"></span>
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item prop="reRegisterPassword">
                <el-input
                  show-password
                  type="password"
                  size="large"
                  placeholder="请再次输入密码"
                  v-model="formData.reRegisterPassword"
                >
                  <template #prefix>
                    <span class="iconfont icon-password"></span>
                  </template>
                </el-input>
              </el-form-item>
            </div>
          </transition>
          
          <el-form-item prop="checkCode">
            <div class="check-code-panel">
              <el-input
                size="large"
                placeholder="请输入验证码"
                v-model="formData.checkCode"
                @keyup.enter="doSubmit"
              >
                <template #prefix>
                  <span class="iconfont icon-checkcode"></span>
                </template>
              </el-input>
              <div class="check-code-wrapper" @click="changeCheckCode()" :title="checkCodeLoading ? '加载中...' : '点击刷新验证码'">
                <img
                  v-if="checkCodeInfo.checkCode && !checkCodeLoading"
                  :src="checkCodeInfo.checkCode"
                  class="check-code"
                  alt="验证码"
                />
                <div v-else class="check-code-loading">
                  <span class="iconfont icon-loading"></span>
                </div>
              </div>
            </div>
          </el-form-item>
          
          <el-form-item class="bottom-btn">
            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="submitting"
              @click="doSubmit"
            >
              <span v-if="opType === 0">注册</span>
              <span v-if="opType === 1">登录</span>
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import {
  ref,
  getCurrentInstance,
  nextTick,
  onMounted,
  watch,
} from "vue";
import md5 from "js-md5";
const { proxy } = getCurrentInstance();

import { useLoginStore } from "@/stores/loginStore.js";
const loginStore = useLoginStore();

//验证码
const checkCodeInfo = ref({});
const checkCodeLoading = ref(false);
const changeCheckCode = async () => {
  checkCodeLoading.value = true;
  try {
    const result = await proxy.Request({
      url: proxy.Api.checkCode,
    });
    if (result) {
      checkCodeInfo.value = result.data;
    }
  } finally {
    checkCodeLoading.value = false;
  }
};

//登录，注册 弹出配置
const dialogConfig = ref({
  show: true,
});

const checkRePassword = (rule, value, callback) => {
  if (value !== formData.value.registerPassword) {
    callback(new Error(rule.message));
  } else {
    callback();
  }
};

// 0:注册 1:登录
const opType = ref(1);
const formData = ref({});
const formDataRef = ref();
const submitting = ref(false);
const rules = {
  email: [
    { required: true, message: "请输入邮箱" },
    { validator: proxy.Verify.email, message: "请输入正确的邮箱" },
  ],
  password: [{ required: true, message: "请输入密码" }],
  nickName: [{ required: true, message: "请输入昵称" }],
  registerPassword: [
    { required: true, message: "请输入密码" },
    {
      validator: proxy.Verify.password,
      message: "密码只能是数字，字母，特殊字符 8-18位",
    },
  ],
  reRegisterPassword: [
    { required: true, message: "请再次输入密码" },
    {
      validator: checkRePassword,
      message: "两次输入的密码不一致",
    },
  ],
  checkCode: [{ required: true, message: "请输入图片验证码" }],
};

//重置表单
const resetForm = () => {
  changeCheckCode();
  nextTick(() => {
    formDataRef.value.resetFields();
    formData.value = {};
  });
};

// 登录、注册、重置密码  提交表单
const doSubmit = () => {
  formDataRef.value.validate(async (valid) => {
    if (!valid) {
      return;
    }
    submitting.value = true;
    try {
      const params = {
        ...formData.value,
        checkCodeKey: checkCodeInfo.value.checkCodeKey,
      };
      
      //登录
      if (opType.value === 1) {
        params.password = md5(params.password);
      }
      
      const result = await proxy.Request({
        url: opType.value === 0 ? proxy.Api.register : proxy.Api.login,
        params: params,
        errorCallback: () => {
          changeCheckCode();
        },
      });
      
      if (!result) {
        return;
      }
      
      //注册返回
      if (opType.value === 0) {
        proxy.Message.success("注册成功,请登录");
        showPanel(1);
      } else if (opType.value === 1) {
        proxy.Message.success("登录成功");
        loginStore.setLogin(false);
        // 不保存token到localStorage，完全依赖后端的HttpOnly Cookie
        // 后端已经设置了HttpOnly Cookie，更安全
        loginStore.saveUserInfo(result.data);
      }
    } finally {
      submitting.value = false;
    }
  });
};

const closeDialog = () => {
  dialogConfig.value.show = false;
  loginStore.setLogin(false);
};

const showPanel = (type) => {
  opType.value = type;
  if (loginStore.showLogin) {
    resetForm();
  }
};

// 监听登录弹窗显示，初始化验证码
watch(
  () => loginStore.showLogin,
  (newVal) => {
    if (newVal) {
      opType.value = 1;
      resetForm();
    }
  },
  { immediate: true }
);

onMounted(() => {
  if (loginStore.showLogin) {
    changeCheckCode();
  }
});
</script>

<style lang="scss">
// 隐藏Dialog的滚动条
:deep(.cust-dialog .dialog-body) {
  overflow: hidden !important;
}

.dialog-panel {
  padding: 25px;
  background: #fff;
  
  .tab-panel {
    display: flex;
    margin-bottom: 20px;
    border-bottom: 1px solid #e4e7ed;
    
    .tab-item {
      flex: 1;
      text-align: center;
      padding: 12px 0;
      cursor: pointer;
      font-size: 16px;
      color: #606266;
      transition: color 0.2s;
      position: relative;
      
      &:hover {
        color: var(--blue2, #409eff);
      }
      
      &.active {
        color: var(--blue2, #409eff);
        font-weight: 500;
        
        &::after {
          content: '';
          position: absolute;
          bottom: -1px;
          left: 0;
          right: 0;
          height: 2px;
          background: var(--blue2, #409eff);
        }
      }
    }
  }
  
  .login-register {
    .el-form-item {
      margin-bottom: 18px;
    }
    
    .register-form {
      margin-top: 0;
      
      .el-form-item {
        margin-bottom: 18px;
      }
    }
    
    .bottom-btn {
      margin-top: 15px;
      margin-bottom: 0;
    }
    
    .login-btn {
      width: 100%;
      height: 42px;
    }
  }
}

.check-code-panel {
  display: flex;
  align-items: center;
  gap: 10px;
  
  .el-input {
    flex: 1;
  }
  
  .check-code-wrapper {
    flex-shrink: 0;
    width: 120px;
    height: 40px;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    overflow: hidden;
    cursor: pointer;
    transition: border-color 0.2s;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f5f7fa;
    
    &:hover {
      border-color: var(--blue2, #409eff);
    }
    
    .check-code {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
    
    .check-code-loading {
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--blue2, #409eff);
      
      .iconfont {
        font-size: 18px;
        animation: rotate 1s linear infinite;
      }
    }
  }
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
