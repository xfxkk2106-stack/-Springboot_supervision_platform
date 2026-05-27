import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getRoomInfo, getRoomMembers } from '@/api/room'

export const useRoomStore = defineStore('room', () => {
  const roomInfo = ref(null)
  const members = ref([])
  const loading = ref(false)

  const memberCount = computed(() => members.value.length)
  const onlineMembers = computed(() => members.value.filter(m => m.isOnline))

  async function fetchRoomInfo(roomCode) {
    loading.value = true
    try {
      const res = await getRoomInfo(roomCode)
      roomInfo.value = res.data
    } catch (error) {
      console.error('获取房间信息失败:', error)
    } finally {
      loading.value = false
    }
  }

  async function fetchMembers(roomCode) {
    try {
      const res = await getRoomMembers(roomCode)
      members.value = res.data
    } catch (error) {
      console.error('获取成员列表失败:', error)
    }
  }

  function updateMemberStatus(memberId, isOnline) {
    const member = members.value.find(m => m.id === memberId)
    if (member) {
      member.isOnline = isOnline
    }
  }

  function addMember(member) {
    if (!members.value.find(m => m.id === member.id)) {
      members.value.push(member)
    }
  }

  function removeMember(memberId) {
    const index = members.value.findIndex(m => m.id === memberId)
    if (index !== -1) {
      members.value.splice(index, 1)
    }
  }

  return {
    roomInfo,
    members,
    loading,
    memberCount,
    onlineMembers,
    fetchRoomInfo,
    fetchMembers,
    updateMemberStatus,
    addMember,
    removeMember,
  }
})
