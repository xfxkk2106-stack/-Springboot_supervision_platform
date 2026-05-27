package com.supervision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.supervision.entity.DailyTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyTaskMapper extends BaseMapper<DailyTask> {
    @Select("SELECT dt.*, rm.display_name FROM daily_task dt LEFT JOIN room_member rm ON dt.member_id = rm.id WHERE dt.member_id = #{memberId} AND dt.task_date = #{taskDate} ORDER BY dt.sort_order")
    List<DailyTask> selectByMemberAndDate(@Param("memberId") Long memberId, @Param("taskDate") LocalDate taskDate);

    @Select("SELECT dt.*, rm.display_name FROM daily_task dt LEFT JOIN room_member rm ON dt.member_id = rm.id WHERE rm.room_id = #{roomId} AND dt.task_date = #{taskDate} ORDER BY dt.sort_order")
    List<DailyTask> selectByRoomAndDate(@Param("roomId") Long roomId, @Param("taskDate") LocalDate taskDate);

    @Select("SELECT dt.*, rm.display_name FROM daily_task dt LEFT JOIN room_member rm ON dt.member_id = rm.id WHERE dt.member_id = #{memberId} AND dt.task_date BETWEEN #{startDate} AND #{endDate} ORDER BY dt.task_date DESC, dt.sort_order")
    List<DailyTask> selectByMemberAndDateRange(@Param("memberId") Long memberId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
