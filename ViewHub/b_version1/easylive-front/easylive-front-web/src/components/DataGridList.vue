<template>
  <div v-if="dataSource.list != null && dataSource.list.length == 0">
    <NoData msg="空空如也" />
  </div>
  <div
    class="data-list"
    :style="{ 'grid-template-columns': `repeat(${gridCount}, 1fr)` }"
  >
    <template v-for="item in dataSource.list">
      <slot :data="item"></slot>
    </template>
  </div>
  <div class="pagination" v-if="showPagination && dataSource.pageTotal > 1">
    <el-pagination
      background
      :total="dataSource.totalCount"
      v-model:current-page="dataSource.pageNo"
      layout="prev, pager, next"
      @current-change="handlePageNoChange"
      :page-size="dataSource.pageSize"
    ></el-pagination>
  </div>
</template>

<script setup>
const props = defineProps({
  gridCount: {
    type: Number,
    default: 5,
  },
  dataSource: {
    type: Object,
  },
  showPagination: {
    type: Boolean,
    default: true,
  },
});

const emit = defineEmits(["loadData", "update:pageNo"]);
const handlePageNoChange = (pageNo) => {
  // 通过 emit 通知父组件更新，而不是直接修改 props
  emit("update:pageNo", pageNo);
  emit("loadData");
};
</script>

<style lang="scss" scoped>
.data-list {
  display: grid;
  grid-gap: 20px;
}
.pagination {
  margin-top: 20px;
  padding: 10px 0px 5px 0px;
  text-align: center;
  overflow: hidden;
  height: 50px;
  display: flex;
  justify-content: left;
}
</style>
