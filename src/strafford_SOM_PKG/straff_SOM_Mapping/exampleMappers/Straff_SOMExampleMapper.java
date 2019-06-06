package strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers;

import java.util.concurrent.ExecutorService;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMExampleMapper;
import strafford_SOM_PKG.straff_Features.MonitorJpJpgrp;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
/**
 * class to manage mapping for all examples (prospects and products) in Strafford project
 * @author john
 *
 */
public abstract class Straff_SOMExampleMapper extends SOMExampleMapper  {
	//manage all jps and jpgs seen in project
	public MonitorJpJpgrp jpJpgrpMon;	
	//ref to mt executor
	protected ExecutorService th_exec;

	public Straff_SOMExampleMapper(SOMMapManager _mapMgr, String _exName, String _longExampleName, boolean _shouldValidate) {
		super(_mapMgr,  _exName, _longExampleName, _shouldValidate);
		jpJpgrpMon = ((Straff_SOMMapManager)mapMgr).jpJpgrpMon;
		th_exec = mapMgr.getTh_Exec();
	}
	
	/**
	 * code to execute after examples have had ftrs prepared - this calculates feature vectors
	 */
	protected final void buildFtrVec_Priv() {
		//set here to have most recent, relevant version
		jpJpgrpMon = ((Straff_SOMMapManager)mapMgr).jpJpgrpMon;
		//instance-specific functions for Strafford examples
		buildStraffFtrVec_Priv();		
	}//buildFtrVec_Priv
	protected abstract void buildStraffFtrVec_Priv();
	
	//load date and time of data creation, if exists
	protected final void loadDataCreateDateTime(String subDir) {
		String[] loadDateTimeFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Load(subDir, exampleName+ "CreationDateTime");
		String dateTimeFileName = loadDateTimeFNamePrefixAra[0]+".csv";
		loadDateAndTimeOfDataCreation(dateTimeFileName, exampleName);
	}
	
	protected final void saveDataCreateDateTime() {
		String[] saveDataFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Save(exampleName+"CreationDateTime");
		String dateTimeFileName = saveDataFNamePrefixAra[0]+".csv";
		saveDateAndTimeOfDataCreation(dateTimeFileName, exampleName);
	}
	
}//class Straff_SOMExampleMapper
