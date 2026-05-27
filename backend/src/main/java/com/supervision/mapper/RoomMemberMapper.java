package com.supervision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.supervision.entity.RoomMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RoomMemberMapper extends BaseMapper<RoomMember> {
    @Select("SELECT rm.*, r.room_code FROM room_member rm LEFT JOIN room r ON rm.room_id = r.id WHERE r.room_code = #{roomCode}")
    List<RoomMember> selectByRoomCode(@Param("roomCode") String roomCode);

    @Update("UPDATE room_member SET is_online = 0")
    void resetAllOffline();
}
