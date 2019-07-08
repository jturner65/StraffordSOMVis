package strafford_SOM_PKG.straff_RawDataHandling.data_loaders;

import strafford_SOM_PKG.straff_RawDataHandling.data_loaders.base.Straff_RawDataLoader;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.Straff_JpgrpDescData;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.base.Straff_BaseRawData;

//stream jpg data for descriptions
public class Straff_JpgrpDataLoader extends Straff_RawDataLoader{ 
	public Straff_JpgrpDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected Straff_BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		Straff_JpgrpDescData obj = new Straff_JpgrpDescData(msgObj,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//
