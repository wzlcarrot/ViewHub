<template>
  <div class="chatbot-container" :class="{ 'minimized': isMinimized }">
    <!-- 聊天窗口头部 -->
    <div class="chatbot-header" @click="toggleMinimize">
      <div class="header-left">
        <span class="bot-icon">🤖</span>
        <span class="bot-name">小易智能助手</span>
        <span class="online-status">● 在线</span>
      </div>
      <div class="header-right">
        <span class="minimize-btn">{{ isMinimized ? '▲' : '▼' }}</span>
      </div>
    </div>

    <!-- 聊天内容区域 -->
    <div class="chatbot-body" v-show="!isMinimized">
      <div class="messages-container" ref="messagesContainer">
        <div 
          v-for="(message, index) in messages" 
          :key="index" 
          :class="['message', message.type]"
        >
          <div class="message-avatar">
            {{ message.type === 'bot' ? '🤖' : '👤' }}
          </div>
          <div class="message-content">
            <div class="message-text" v-html="formatMessage(message.text)"></div>
            <!-- 推荐视频卡片 -->
            <div v-if="message.videos && message.videos.length > 0" class="video-recommendations">
              <div 
                v-for="video in message.videos" 
                :key="video.videoId" 
                class="video-card"
                @click="goToVideo(video.videoId)"
              >
                <div class="video-title">📹 {{ video.videoName }}</div>
                <div class="video-info">
                  <span>👁 {{ formatCount(video.playCount) }}</span>
                  <span>💬 {{ formatCount(video.commentCount) }}</span>
                </div>
              </div>
            </div>
            <div class="message-time">{{ formatTime(message.timestamp) }}</div>
          </div>
        </div>
        
        <!-- 加载动画 -->
        <div v-if="isLoading" class="message bot">
          <div class="message-avatar">🤖</div>
          <div class="message-content">
            <div class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chatbot-input">
        <input 
          v-model="userInput" 
          @keyup.enter="sendMessage"
          :disabled="isLoading"
          placeholder="输入消息，和我聊天吧..."
          class="input-field"
        />
        <button 
          @click="sendMessage" 
          :disabled="isLoading || !userInput.trim()"
          class="send-btn"
        >
          {{ isLoading ? '发送中...' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, getCurrentInstance, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { marked } from 'marked'

const { proxy } = getCurrentInstance()
const router = useRouter()

// 配置 marked 选项
marked.setOptions({
  breaks: true,        // 支持换行
  gfm: true,          // 启用 GitHub 风格的 Markdown
  headerIds: false,   // 不生成标题 ID
  mangle: false       // 不混淆邮箱地址
})

const props = defineProps({
  searchKeyword: {
    type: String,
    default: ''
  }
})

const isMinimized = ref(false)
const userInput = ref('')
const isLoading = ref(false)
const messages = ref([
  {
    type: 'bot',
    text: '你好！我是小易🤖，你的智能视频助手。\n\n我可以帮你：\n• 推荐视频\n• 解答问题\n• 聊天互动\n\n试试问我"推荐一些编程视频"吧！',
    timestamp: Date.now()
  }
])

const messagesContainer = ref(null)

// 切换最小化
const toggleMinimize = () => {
  isMinimized.value = !isMinimized.value
}

// 格式化数字
const formatCount = (count) => {
  if (!count) return 0
  if (count >= 10000) {
    return (count / 10000).toFixed(1) + 'w'
  }
  return count
}

// 格式化时间
const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 格式化消息（支持 Markdown）
const formatMessage = (text) => {
  try {
    // 使用 marked 解析 Markdown
    return marked.parse(text)
  } catch (error) {
    console.error('Markdown 解析失败:', error)
    // 降级处理：简单的换行替换
    return text.replace(/\n/g, '<br>')
  }
}

// 跳转到视频详情
const goToVideo = (videoId) => {
  router.push(`/video/${videoId}`)
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 添加消息
const addMessage = (type, text, videos = null) => {
  messages.value.push({ 
    type, 
    text, 
    videos,
    timestamp: Date.now()
  })
  scrollToBottom()
}

// 发送消息到后端 AI
const sendMessage = async () => {
  if (!userInput.value.trim() || isLoading.value) return
  
  const message = userInput.value.trim()
  
  // 添加用户消息
  addMessage('user', message)
  userInput.value = ''
  
  // 显示加载状态
  isLoading.value = true
  scrollToBottom()
  
  try {
    // 直接调用聊天接口（已包含视频推荐功能）
    const chatResult = await proxy.Request({
      url: proxy.Api.aiChat,
      params: {
        message: message
      }
    })
    
    isLoading.value = false
    
    if (chatResult && chatResult.data && chatResult.data.message) {
      addMessage('bot', chatResult.data.message)
    } else {
      addMessage('bot', '抱歉，我遇到了一些问题。请稍后再试。')
    }
  } catch (error) {
    console.error('AI 对话失败:', error)
    isLoading.value = false
    addMessage('bot', '抱歉，AI 服务暂时不可用😅\n可能是网络问题或后端服务未启动，请稍后再试。')
  }
}

// 监听搜索关键词变化
watch(() => props.searchKeyword, (newKeyword) => {
  if (newKeyword) {
    // 自动发送消息
    setTimeout(() => {
      userInput.value = `推荐关于"${newKeyword}"的视频`
      sendMessage()
    }, 500)
  }
})

// 组件挂载时的欢迎消息
onMounted(() => {
  scrollToBottom()
})
</script>

<style lang="scss" scoped>
.chatbot-container {
  position: fixed;
  right: 20px;
  bottom: 20px;
  width: 380px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  z-index: 1000;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &.minimized {
    height: 60px;
  }
}

.chatbot-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 16px 20px;
  border-radius: 16px 16px 0 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s;

  &:hover {
    background: linear-gradient(135deg, #5568d3 0%, #6a3f8f 100%);
  }

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .bot-icon {
      font-size: 28px;
      animation: wave 2s ease-in-out infinite;
    }

    .bot-name {
      font-weight: 600;
      font-size: 16px;
      letter-spacing: 0.5px;
    }

    .online-status {
      font-size: 12px;
      background: rgba(255, 255, 255, 0.25);
      padding: 3px 10px;
      border-radius: 12px;
      animation: pulse 2s ease-in-out infinite;
    }
  }

  .minimize-btn {
    font-size: 20px;
    transition: transform 0.3s;
    opacity: 0.9;
  }
}

.chatbot-body {
  display: flex;
  flex-direction: column;
  height: 520px;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: linear-gradient(to bottom, #f8f9fa 0%, #ffffff 100%);

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: #d0d0d0;
    border-radius: 3px;
    
    &:hover {
      background: #b0b0b0;
    }
  }
}

.message {
  display: flex;
  margin-bottom: 20px;
  animation: slideIn 0.3s ease-out;

  &.user {
    flex-direction: row-reverse;

    .message-content {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border-radius: 18px 18px 4px 18px;
      margin-left: auto;
      margin-right: 0;
    }

    .message-time {
      text-align: right;
      color: rgba(255, 255, 255, 0.8);
    }
  }

  &.bot {
    .message-content {
      background: white;
      color: #333;
      border-radius: 18px 18px 18px 4px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    }
  }
}

.message-avatar {
  font-size: 36px;
  margin: 0 12px;
  flex-shrink: 0;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}

.message-content {
  max-width: 75%;
  padding: 14px 18px;
  position: relative;
}

.message-text {
  line-height: 1.6;
  word-wrap: break-word;
  font-size: 14px;

  // Markdown 样式
  :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
    margin: 12px 0 8px 0;
    font-weight: 600;
    line-height: 1.4;
  }

  :deep(h1) { font-size: 20px; }
  :deep(h2) { font-size: 18px; }
  :deep(h3) { font-size: 16px; }
  :deep(h4) { font-size: 15px; }

  :deep(p) {
    margin: 8px 0;
    line-height: 1.6;
  }

  :deep(ul), :deep(ol) {
    margin: 8px 0;
    padding-left: 24px;
  }

  :deep(li) {
    margin: 4px 0;
    line-height: 1.5;
  }

  :deep(strong) {
    font-weight: 600;
    color: #333;
  }

  :deep(em) {
    font-style: italic;
    color: #555;
  }

  :deep(code) {
    background: rgba(0, 0, 0, 0.06);
    padding: 2px 6px;
    border-radius: 3px;
    font-family: 'Courier New', monospace;
    font-size: 13px;
    color: #e83e8c;
  }

  :deep(pre) {
    background: rgba(0, 0, 0, 0.06);
    padding: 12px;
    border-radius: 6px;
    overflow-x: auto;
    margin: 8px 0;

    code {
      background: none;
      padding: 0;
      color: #333;
    }
  }

  :deep(blockquote) {
    border-left: 4px solid #667eea;
    padding-left: 12px;
    margin: 8px 0;
    color: #666;
    font-style: italic;
  }

  :deep(a) {
    color: #667eea;
    text-decoration: none;
    
    &:hover {
      text-decoration: underline;
    }
  }

  :deep(hr) {
    border: none;
    border-top: 1px solid #eee;
    margin: 12px 0;
  }

  :deep(table) {
    border-collapse: collapse;
    width: 100%;
    margin: 8px 0;
    font-size: 13px;

    th, td {
      border: 1px solid #ddd;
      padding: 8px;
      text-align: left;
    }

    th {
      background: rgba(102, 126, 234, 0.1);
      font-weight: 600;
    }
  }
}

.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 6px;
  opacity: 0.7;
}

.video-recommendations {
  margin-top: 12px;
}

.video-card {
  background: rgba(0, 0, 0, 0.04);
  padding: 12px;
  border-radius: 10px;
  margin-top: 10px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid rgba(0, 0, 0, 0.06);

  &:hover {
    background: rgba(102, 126, 234, 0.1);
    transform: translateX(4px);
    border-color: rgba(102, 126, 234, 0.3);
  }

  .video-title {
    font-weight: 600;
    margin-bottom: 6px;
    font-size: 13px;
    color: #333;
  }

  .video-info {
    display: flex;
    gap: 16px;
    font-size: 12px;
    color: #666;
  }
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 8px 0;

  span {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #667eea;
    animation: typing 1.4s ease-in-out infinite;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }

    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}

.chatbot-input {
  display: flex;
  padding: 16px;
  background: white;
  border-top: 1px solid #eee;
  border-radius: 0 0 16px 16px;
  gap: 10px;

  .input-field {
    flex: 1;
    padding: 12px 16px;
    border: 2px solid #e0e0e0;
    border-radius: 24px;
    outline: none;
    font-size: 14px;
    transition: all 0.2s;

    &:focus {
      border-color: #667eea;
      box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
    }

    &:disabled {
      background: #f5f5f5;
      cursor: not-allowed;
    }
  }

  .send-btn {
    padding: 12px 24px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    border: none;
    border-radius: 24px;
    cursor: pointer;
    font-weight: 600;
    font-size: 14px;
    transition: all 0.2s;
    white-space: nowrap;

    &:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
    }

    &:active:not(:disabled) {
      transform: translateY(0);
    }

    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
      transform: none;
    }
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-10px);
  }
}

@keyframes wave {
  0%, 100% {
    transform: rotate(0deg);
  }
  25% {
    transform: rotate(-10deg);
  }
  75% {
    transform: rotate(10deg);
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}
</style>
