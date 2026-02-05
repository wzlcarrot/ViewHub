import axios from 'axios'
import { ElLoading } from 'element-plus'
import Message from '../utils/Message'
import VueCookies from 'vue-cookies'

import { useLoginStore } from "@/stores/loginStore"

const contentTypeForm = 'application/x-www-form-urlencoded;charset=UTF-8'
const contentTypeJson = 'application/json'
const responseTypeJson = 'json'
let loading = null;
const instance = axios.create({
    withCredentials: true,
    baseURL: "/api",
    timeout: 10 * 1000,
});
//请求前拦截器
instance.interceptors.request.use(
    (config) => {
        if (config.showLoading) {
            loading = ElLoading.service({
                lock: true,
                text: '加载中......',
                background: 'rgba(0, 0, 0, 0.7)',
            });
        }
        return config;
    },
    (error) => {
        if (error.config && error.config.showLoading && loading) {
            loading.close();
        }
        Message.error("请求发送失败");
        return Promise.reject("请求发送失败");
    }
);
//请求后拦截器
instance.interceptors.response.use(
    (response) => {
        const { showLoading, errorCallback, showError = true, responseType } = response.config;
        if (showLoading && loading) {
            loading.close()
        }
        const responseData = response.data;
        if (responseType === "arraybuffer" || responseType === "blob") {
            return responseData;
        }
        //正常请求
        if (responseData.code === 200) {
            return responseData;
        } else if (responseData.code === 901) {
            const loginStore = useLoginStore();
            //登录超时
            loginStore.setLogin(true);
            return Promise.reject({ showError: false });
        } else {
            //其他错误
            if (errorCallback) {
                errorCallback(responseData);
            }
            return Promise.reject({ showError: showError, msg: responseData.info });
        }
    },
    (error) => {
        if (error.config && error.config.showLoading && loading) {
            loading.close();
        }
        
        let errorMsg = "网络异常";
        if (error.code === 'ECONNABORTED' || error.message === 'timeout of 10000ms exceeded') {
            errorMsg = "请求超时，请稍后重试";
        } else if (error.response) {
            // 服务器返回了错误状态码
            const status = error.response.status;
            if (status >= 500) {
                errorMsg = "服务器错误，请稍后重试";
            } else if (status === 404) {
                errorMsg = "请求的资源不存在";
            } else if (status === 403) {
                errorMsg = "没有权限访问该资源";
            } else {
                errorMsg = `请求失败: ${status}`;
            }
        } else if (error.request) {
            // 请求已发出但没有收到响应
            errorMsg = "网络连接失败，请检查网络";
        }
        
        return Promise.reject({ showError: true, msg: errorMsg })
    }
);

const request = (config) => {
    const { url, params, dataType, showLoading = false, responseType = responseTypeJson, showError = true } = config;
    const contentType = (dataType !== null && dataType === 'json') ? contentTypeJson : contentTypeForm;
    const formData = new FormData();// 创建form对象
    for (const key in params) {
        formData.append(key, params[key] === undefined ? "" : params[key]);
    }
    // 不读取localStorage，完全依赖HttpOnly Cookie
    // HttpOnly Cookie会自动在请求中携带（withCredentials: true）
    const headers = {
        'Content-Type': contentType,
        'X-Requested-With': 'XMLHttpRequest',
        // 不设置token请求头，让后端从HttpOnly Cookie中读取
    }
    return instance.post(url, formData, {
        onUploadProgress: (event) => {
            if (config.uploadProgressCallback) {
                config.uploadProgressCallback(event);
            }
        },
        responseType: responseType,
        headers: headers,
        withCredentials: true, // 确保携带Cookie
        showLoading: showLoading,
        errorCallback: config.errorCallback,
        showError: showError,
    }).catch(error => {
        if (error.showError) {
            Message.error(error.msg);
        }
        return null;
    });
};

export default request;
