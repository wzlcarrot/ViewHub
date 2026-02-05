
<template>
  <el-config-provider :locale="zhCn" :message="config">
    <router-view></router-view>
  </el-config-provider>
</template>

<script setup>
import { onBeforeMount, ref } from "vue";
import { ElConfigProvider } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import { Api } from "@/utils/Api.js";
import VueCookies from "vue-cookies";
import Request from "@/utils/Request";
import { useLoginStore } from "@/stores/loginStore.js";
import { useSysSettingStore } from "@/stores/sysSettingStore.js";
import { useCategoryStore } from "@/stores/categoryStore.js";
import { useSearchHistoryStore } from "@/stores/searchHisotryStore.js";
import { useRoute, useRouter } from "vue-router";

import FingerprintJS from "@fingerprintjs/fingerprintjs";

const loginStore = useLoginStore();

const sysSettingStore = useSysSettingStore();

const categoryStore = useCategoryStore();

const searchHistoryStore = useSearchHistoryStore();

const route = useRoute();
const router = useRouter();

const config = ref({
  max: 1,
});

//自动登录
const autoLogin = async () => {
  try {
    // 直接调用后端接口，后端会从HttpOnly Cookie中读取token
    // 前端不需要读取token，完全依赖HttpOnly Cookie
    let result = await Request({
      url: Api.autoLogin,
      showError: false, // 自动登录失败不显示错误提示
    });
    // result为null表示请求失败（网络错误等），此时不处理，保持当前状态
    if (!result) {
      loginStore.setAutoLoginChecked(true);
      return;
    }
    // result.data为null表示没有token或token无效，这是正常情况（用户未登录）
    if (result.data) {
      saveLoginInfo(result.data);
    } else {
      saveLoginInfo(null);
    }
    // 标记自动登录检查已完成
    loginStore.setAutoLoginChecked(true);
  } catch (error) {
    // 捕获异常，但不影响页面加载
    // 生产环境不输出错误日志
    if (import.meta.env.DEV) {
      console.error("自动登录异常:", error);
    }
    // 即使出错也要标记为已检查，避免路由守卫一直等待
    loginStore.setAutoLoginChecked(true);
  }
};
const saveLoginInfo = (loginInfo) => {
  if (!loginInfo) {
    // 没有登录信息，清空store中的用户信息
    loginStore.saveUserInfo({});
    // 不操作localStorage，完全依赖HttpOnly Cookie
  } else {
    // 有登录信息，保存到store
    loginStore.saveUserInfo(loginInfo);
    // 不保存token到localStorage，完全依赖后端的HttpOnly Cookie
  }
};

//获取系统设置信息
const getSysSetting = async () => {
  let result = await Request({
    url: Api.getSysSetting,
  });
  if (!result) {
    return;
  }
  sysSettingStore.saveSetting(result.data);
};

let categoryList = [];
let categoryMap = {};

//获取分类
const loadCategory = async () => {
  categoryStore.saveCategoryMap({})
  let result = await Request({
    url: Api.loadAllCategory,
  });
  if (!result) {
    return;
  }
  categoryList = result.data;

  result.data.forEach((element) => {
    categoryMap[element.categoryCode] = element;
    element.children.forEach((sub) => {
      categoryMap[sub.categoryCode] = sub;
    });
  });
  categoryStore.saveCategoryMap(categoryMap);
  categoryStore.saveCategoryList(categoryList);
};

const getDeviceId = async () => {
  let deviceId = VueCookies.get("deviceId");
  if (!deviceId) {
    const fpPromise = await FingerprintJS.load();
    const result = await fpPromise.get();
    deviceId = result.visitorId;
    VueCookies.set("deviceId", deviceId, -1);
  }
  loginStore.saveDeviceId(deviceId);
};

onBeforeMount(() => {
  getDeviceId();
  autoLogin();
  getSysSetting();
  loadCategory();
  searchHistoryStore.initHistory();
});
</script>

<style scoped>
</style>
