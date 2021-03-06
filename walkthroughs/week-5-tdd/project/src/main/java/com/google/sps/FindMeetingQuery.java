// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps;

import java.util.Collection;
import java.util.*;
import java.lang.*;

public final class FindMeetingQuery {
  /**
   * Returns the times when the meeting could happen on a certain day given meeting request.
   */
  public Collection<TimeRange> query(Collection<Event> events, MeetingRequest request) {
    Set<String> attendees =  new HashSet<String>();
    attendees.addAll(request.getAttendees()); 
    Set<String> optionalAttendees = new HashSet<String>();
    optionalAttendees.addAll(request.getOptionalAttendees());
    int reqDuration = (int) request.getDuration();

    //if requested duration is longer than 24hrs, there is no time slot to accommodate that
    if (reqDuration > 24*60){
      return new ArrayList<TimeRange>();
    }

    //if there are no scheduled events, the entire day is free
    if (events.isEmpty()){
      return new ArrayList<TimeRange>(Arrays.asList(TimeRange.WHOLE_DAY));
    }

    //accumulates an arraylist of all times that it is not possible to meet (blocked time)
    //a time is blocked if there are attendees in the meeting request and in the event
    List<TimeRange> notPossible= new ArrayList<TimeRange>();
    List<TimeRange> notPossibleOptional= new ArrayList<TimeRange>();
    for (Event event : events){
      Set<String> eventAttendees = event.getAttendees();
      Set<String> overlap = new HashSet<>(eventAttendees);
      Set<String> overlapOptional = new HashSet<>(eventAttendees);

      overlap.retainAll(attendees);
      overlapOptional.retainAll(optionalAttendees);

      if (!overlap.isEmpty()){
        notPossible.add(event.getWhen());
      }

      if (!overlapOptional.isEmpty()){
        notPossibleOptional.add(event.getWhen());
      }
    }

    //if there are no blocked times, the entire day is free
    if (notPossible.isEmpty() && notPossibleOptional.isEmpty()){
      return new ArrayList<TimeRange>(Arrays.asList(TimeRange.WHOLE_DAY));
      
    }

    if (notPossible.isEmpty() && !notPossibleOptional.isEmpty()){
      return findFreeTimes(consolidate(notPossibleOptional), reqDuration);
    }

    if (!notPossible.isEmpty() && notPossibleOptional.isEmpty()){
      return findFreeTimes(consolidate(notPossible), reqDuration);
    }

    List<TimeRange> findTimes = new ArrayList<TimeRange>();
    findTimes.addAll(notPossibleOptional);
    findTimes.addAll(notPossible);
    List<TimeRange> bothFree = findFreeTimes(consolidate(findTimes), reqDuration);

    if (bothFree.isEmpty()){
        return findFreeTimes(consolidate(notPossible), reqDuration);
    }

    return bothFree;
  }   

  //combines overlapping required blocked times into a consolidated arraylist
  private List<TimeRange> consolidate(List<TimeRange> times){
    Collections.sort(times, TimeRange.ORDER_BY_START);
    TimeRange[] blockedTimes = ((List<TimeRange>) times).toArray(new TimeRange[times.size()]); 
    List<TimeRange> tmp = new ArrayList<TimeRange>();
    tmp.add(blockedTimes[0]);
    int tmpIdx = 0;

    for (int i=0; i < blockedTimes.length; i++){
      if (blockedTimes[i].overlaps(tmp.get(tmpIdx))){
        tmp.set(tmpIdx, 
                TimeRange.fromStartEnd(Math.min(tmp.get(tmpIdx).start(), blockedTimes[i].start()), 
                                       Math.max(tmp.get(tmpIdx).end(), blockedTimes[i].end()) - 1, 
                                       true));
      }

      else {
        tmpIdx += 1;
        tmp.add(blockedTimes[i]);
      }
    }

      return tmp;
  }

  //finds all available (free) times between blocked time that are of requested duration or longer
  private List<TimeRange> findFreeTimes(List<TimeRange> times, int reqDuration){
    TimeRange[] findFree = ((List<TimeRange>) times).toArray(new TimeRange[times.size()]); 
    List<TimeRange> tmp = new ArrayList<TimeRange>();

    int start;
    int end;
    for (int i = -1; i < findFree.length; i++){
      if (i == -1){
        start = 0;
        end = findFree[0].start();  
      } 

      else if (i == findFree.length - 1){
        start = findFree[i].end();
        end = 24*60;
      }

      else {
        start = findFree[i].end();
        end = findFree[i+1].start();
      }

      if (TimeRange.fromStartEnd(start, end, true).duration() >= reqDuration){
        tmp.add(TimeRange.fromStartEnd(start, end - 1, true));
      }
    }
    return tmp;
  }
}