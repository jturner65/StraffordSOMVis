package Utils;

import java.time.Instant;

public class myTimeMgr {

	//time of current process start, from initial construction of mapmgr - TODO use this to monitor specific process time elapsed.  set to 0 at beginning of a particular process, then measure time elapsed in process
	protected long curProcStartTime;
	//time mapMgr built, in millis - used as offset for instant to provide smaller values for timestamp
	protected final long mapMgrBuiltTime;

	public myTimeMgr(long _mapMgrBuiltTime) {mapMgrBuiltTime=_mapMgrBuiltTime;}
	//////////////////////////
	// time functions
	//set time of current process start - all future time measurements will measure from here
	public void setCurProcStartTime(long _curProcStartTime) {curProcStartTime=_curProcStartTime;}
	
	//get time from "start time" (instancing of owning object)
	public long getCurTime() {			
		Instant instant = Instant.now();
		return instant.toEpochMilli() - mapMgrBuiltTime;//milliseconds since 1/1/1970, subtracting when mapmgr was built to keep millis low		
	}//getCurTime() 
	
	//returns a positive int value in millis of current world time since sim start
	public long getCurRunTimeForProc() {	return getCurTime() - curProcStartTime;}
	
	public String getTimeStrFromProcStart() {return  getTimeStrFromPassedMillis(getCurRunTimeForProc());}
	//get a decent display of passed milliseconds elapsed
	//	long msElapsed = getCurRunTimeForProc();
	public String getTimeStrFromPassedMillis(long msElapsed) {
		long ms = msElapsed % 1000, sec = (msElapsed / 1000) % 60, min = (msElapsed / 60000) % 60, hr = (msElapsed / 3600000) % 24;	
		String res = String.format("%02d:%02d:%02d.%03d", hr, min, sec, ms);
		return res;
	}//getTimeStrFromPassedMillis	


}//myTimeMgr
