package base_SOM_Objects.som_utils.runners;

import java.util.concurrent.ExecutorService;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.SOMExample;
import base_Utils_Objects.io.MessageObject;

/**
 * manage a runner that will launch a number of callables suitable for machine arch to manage multi-threaded calcs
 * @author john
 *
 */
public abstract class SOM_MapRunners {
	protected final SOM_MapManager mapMgr;
	protected final MessageObject msgObj;
	protected final boolean canMultiThread;
	protected final String dataTypName;
	protected final SOMExample[] exData;
	protected final int numUsableThreads;
	//ref to thread executor
	protected final ExecutorService th_exec;	
	
	public SOM_MapRunners(SOM_MapManager _mapMgr, ExecutorService _th_exec, SOMExample[] _exData, String _dataTypName, boolean _forceST) {
		mapMgr = _mapMgr; 
		msgObj = mapMgr.getMsgObj();
		numUsableThreads = mapMgr.getNumUsableThreads()-1;
		canMultiThread = mapMgr.isMTCapable() && !_forceST;
		th_exec = _th_exec;
		exData = _exData;
		dataTypName = _dataTypName;
		
	}//ctor
	
	//determine how many values should be per thread, if 
	public int calcNumPerThd(int numVals, int numThds) {
		//return (int) Math.round((numVals + Math.round(numThds/2))/(1.0*numThds));
		return (int) ((numVals -1)/(1.0*numThds)) + 1;
	}//calcNumPerThd
	
	protected abstract int getNumPerPartition();

	/**
	 * launch this runner
	 */
	public final void runMe() {
		if(canMultiThread) {
			int numPartitions = exData.length/getNumPerPartition();
			if(numPartitions < 1) {numPartitions = 1;}
			int numPerPartition = calcNumPerThd(exData.length,numPartitions);
			runMe_Indiv_MT(numPartitions, numPerPartition);
		} else {
			runMe_Indiv_ST();
		}
		runMe_Indiv_End();
	}//runMe()
	
	protected abstract void runMe_Indiv_MT(int numPartitions, int numPerPartition);
	
	protected abstract void runMe_Indiv_ST();
	
	protected abstract void runMe_Indiv_End();
	
	
}// class SOM_MapRunners
