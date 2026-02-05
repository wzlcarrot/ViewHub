<template>
  <div class="data-list">
    <DataLoadMoreList :dataSource="dataSource" :loading="loadingData" @loadData="loadDataList">
      <template #default="{ data }">
        <VideoItem :data="data" :marginTop="20" ref="videoItemRef"></VideoItem>
      </template>
    </DataLoadMoreList>
  </div>
</template>

<script setup>
import { useCategoryStore } from '@/stores/categoryStore.js'
const categoryStore = useCategoryStore()

import { mitter } from '@/eventbus/eventBus.js'
import {
  ref,
  getCurrentInstance,
  watch,
} from 'vue'
const { proxy } = getCurrentInstance()
import { useRoute, useRouter } from 'vue-router'
const route = useRoute()
const router = useRouter()

const categoryMap = categoryStore.categoryMap
const categoryIdInfo = ref({})

const convertCode2Id = (pCategoryCode, categoryCode) => {
  const pCategoryId = pCategoryCode && categoryMap[pCategoryCode]
    ? categoryMap[pCategoryCode].categoryId
    : ''
  const categoryId = categoryCode && categoryMap[categoryCode]
    ? categoryMap[categoryCode].categoryId
    : ''
  categoryIdInfo.value = {
    pCategoryId,
    categoryId,
  }
}
const loadingData = ref(false)
const dataSource = ref({})
const loadDataList = async () => {
  const params = {
    pageNo: dataSource.value.pageNo,
    ...categoryIdInfo.value
  }
  loadingData.value = true
  const result = await proxy.Request({
    url: proxy.Api.loadVideo,
    params,
  })
  loadingData.value = false
  if (!result) {
    return
  }
  const dataList = dataSource.value.list
  dataSource.value = Object.assign({}, result.data)
  if (result.data.pageNo > 1) {
    dataSource.value.list = dataList.concat(result.data.list)
  }
}

const initData = () => {
  convertCode2Id(route.params.pCategoryCode, route.params.categoryCode)
  loadDataList()
}

const categoryInit = ref(false)
watch(
  () => route.params,
  (newVal, oldVal) => {
    if (newVal) {
      if (!categoryInit.value) {
        return
      }
      categoryStore.setCureentPCategory(route.params.pCategoryCode)
      initData()
    }
  },
  { immediate: true, deep: true }
)

watch(
  () => categoryStore.categoryMap,
  (newVal, oldVal) => {
    if (!newVal || Object.keys(newVal).length === 0) {
      return
    }
    categoryInit.value = true
    categoryStore.setCureentPCategory(route.params.pCategoryCode)
    initData()
  },
  { immediate: true, deep: true }
)
</script>

<style lang="scss" scoped>
</style>
