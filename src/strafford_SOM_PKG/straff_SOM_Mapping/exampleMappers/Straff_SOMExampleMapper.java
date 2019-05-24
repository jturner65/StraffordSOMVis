package strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers;

import java.util.concurrent.ExecutorService;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMExampleMapper;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_Utils.MonitorJpJpgrp;
/**
 * class to manage mapping for all examples (prospects and products) in project
 * @author john
 *
 */
public abstract class Straff_SOMExampleMapper extends SOMExampleMapper  {
	//manage all jps and jpgs seen in project
	public MonitorJpJpgrp jpJpgrpMon;	
	//ref to mt executor
	protected ExecutorService th_exec;

	public Straff_SOMExampleMapper(SOMMapManager _mapMgr, String _exName) {
		super(_mapMgr,  _exName);
		jpJpgrpMon = ((Straff_SOMMapManager)mapMgr).jpJpgrpMon;
		th_exec = mapMgr.getTh_Exec();
	}
	
	
}//class Straff_SOMExampleMapper
