<template>
  <div class="ai-search-body">
    <div class="search-panel">
      <el-input 
        placeholder="试试AI搜索：我想看一个开开心心快快乐乐的视频" 
        v-model="keyword" 
        size="large" 
        @keyup.enter="search"
      >
        <template #prefix>
          <span class="iconfont icon-search"></span>
        </template>
        <template #suffix>
          <el-button type="primary" @click="search" class="search-btn">
            [object Object]</el-button>
        </template>
      </el-input>
    </div>

    <!-- AI推荐话术 -->
    <div class="ai-recommendation" v-if="aiRecommendation">
      <div class="ai-avatar">🤖</div>
      <div class="ai-text">
        <div class="ai-label">AI助手</div>
        <div class="ai-content">{{ aiRecommendation }}</div>
        <el-tag v-if="aiEnabled" type="success" size="small" class="ai-tag">
          AI增强
        </el-tag>
        <el-tag v-else type="info" size="small" class="ai-tag">
          智能模式
        </el-tag>
      </div>
    </div>

    <!-- 视频列表 -->
    <div class="video-section" v-if="videoList.length > 0">
      <div class="section-title">
        <span class="title-icon">🎬</span>
        为您推荐 {{ totalCount }} 个视频
      </div>
      
      <DataGridList :gridCount="6" :dataSource="{ list: videoList }">
        <template #default="{ data }">
          <VideoItem :data="data"></VideoItem>
        </template>
      </DataGridList>
    </div>

    <!-- 空状态 -->
    <div class="empty-state" v-if="searched && videoList.length === 0">
      <div class="empty-icon">😔</div>
      <div class="empty-text">暂时没有找到相关视频</div>
      <div class="empty-tip">试试换个关键词吧</div>
    </div>

    <!-- 搜索示例 -->
    <div class="search-examples" v-if="!searched">
      <div class="examples-title">💡 试试这些搜索</div>
      <div class="example-tags">
        <el-tag 
          v-for="example in examples" 
          :key="example"
          @click="searchExample(example)"
          class="example-tag"
        >
          {{ example }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import {
  ref,
  getCurrentInstance,
  onMounted,
} from 'vue'
import { useRouter, useRoute } from 'vue-router'

const { proxy } = getCurrentInstance()
const router = useRouter()
const route = useRoute()

import { useNavAction } from '@/stores/navActionStore'
const navActionStore = useNavAction()

const keyword = ref(route.query.keyword || '')
const aiRecommendation = ref('')
const aiEnabled = ref(false)
const videoList = ref([])
const totalCount = ref(0)
const searched = ref(false)

// 搜索示例
const examples = [
  '我想看开心的视频',
  '感动人心的故事',
  '学习编程教程',
  '美食制作过程',
  '旅游风景视频',
  '搞笑娱乐内容'
]

const search = async () => {
  if (!keyword.value) {
    proxy.Message.warning('请输入搜索内容')
    return
  }

  searched.value = true
  
  let result = await proxy.Request({
    url: proxy.Api.aiSearch,
    params: {
      query: keyword.value,
      pageNo: 1,
    },
  })

  if (!result) {
    return
  }

  const data = result.data
  aiRecommendation.value = data.aiRecommendation
  aiEnabled.value = data.aiEnabled
  videoList.value = data.videoList || []
  totalCount.value = data.totalCount || 0
}

const searchExample = (example) => {
  keyword.value = example
  search()
}

const goDetail = (videoId) => {
  router.push(`/video/${videoId}`)
}

onMounted(() => {
  navActionStore.setShowHeader(false)
  navActionStore.setFixedHeader(false)
  navActionStore.setFixedCategory(false)
  navActionStore.setShowCategory(false)
  navActionStore.setForceFixedHeader(true)

  // 如果URL中有关键词，自动搜索
  if (keyword.value) {
    search()
  }
})
</script>

<style lang="scss" scoped>
.ai-search-body {
  padding-top: 80px;
  max-width: 1400px;
  margin: 0 auto;
  padding-left: 20px;
  padding-right: 20px;

  .search-panel {
    margin: 30px auto;
    max-width: 800px;

    :deep(.el-input) {
      height: 60px;
      border-radius: 30px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);

      .el-input__wrapper {
        border-radius: 30px;
      }
    }

    .icon-search {
      color: var(--blue);
      font-weight: bold;
      font-size: 20px;
    }

    .search-btn {
      padding: 22px 40px;
      border-radius: 25px;
      font-size: 16px;
      font-weight: bold;
    }
  }

  .ai-recommendation {
    display: flex;
    gap: 20px;
    max-width: 900px;
    margin: 40px auto;
    padding: 25px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-radius: 20px;
    box-shadow: 0 8px 30px rgba(102, 126, 234, 0.3);
    animation: slideIn 0.5s ease-out;

    .ai-avatar {
      font-size: 40px;
      flex-shrink: 0;
    }

    .ai-text {
      flex: 1;
      color: white;

      .ai-label {
        font-size: 14px;
        font-weight: bold;
        margin-bottom: 8px;
        opacity: 0.9;
      }

      .ai-content {
        font-size: 18px;
        line-height: 1.6;
        margin-bottom: 10px;
      }

      .ai-tag {
        margin-top: 5px;
      }
    }
  }

  .video-section {
    margin-top: 40px;

    .section-title {
      font-size: 20px;
      font-weight: bold;
      color: #333;
      margin-bottom: 20px;
      display: flex;
      align-items: center;
      gap: 10px;

      .title-icon {
        font-size: 24px;
      }
    }
  }

  .empty-state {
    text-align: center;
    padding: 80px 20px;

    .empty-icon {
      font-size: 80px;
      margin-bottom: 20px;
    }

    .empty-text {
      font-size: 20px;
      color: #666;
      margin-bottom: 10px;
    }

    .empty-tip {
      font-size: 14px;
      color: #999;
    }
  }

  .search-examples {
    max-width: 800px;
    margin: 60px auto;
    text-align: center;

    .examples-title {
      font-size: 18px;
      color: #666;
      margin-bottom: 20px;
    }

    .example-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 15px;
      justify-content: center;

      .example-tag {
        cursor: pointer;
        padding: 10px 20px;
        font-size: 15px;
        transition: all 0.3s ease;

        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        }
      }
    }
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>

