import { defineStore } from 'pinia'

const useLoginStore = defineStore("loginState", {
    state: () => {
        return {
            showLogin: false,
            userInfo: {

            },
            messageNoReadCount: 0,
            deviceId: null,
            // 是否已经执行过自动登录校验，避免重复请求
            isAutoLoginChecked: false,
        }
    },
    getters: {
        // 判断是否已登录
        isLoggedIn: (state) => {
            return Object.keys(state.userInfo).length > 0;
        }
    },
    actions: {
        setLogin(show) {
            this.showLogin = show;
        },
        saveUserInfo(info) {
            this.userInfo = info;
        },
        saveMessageNoReadCount(count) {
            this.messageNoReadCount = count;
        },
        readMessageCount(count) {
            this.messageNoReadCount -= count;
        },
        saveDeviceId(deviceId) {
            this.deviceId = deviceId;
        },
        setAutoLoginChecked(flag) {
            this.isAutoLoginChecked = flag;
        }
    }
})
export {
    useLoginStore
};
