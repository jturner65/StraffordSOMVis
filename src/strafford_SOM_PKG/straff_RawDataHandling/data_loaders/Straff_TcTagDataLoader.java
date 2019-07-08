package strafford_SOM_PKG.straff_RawDataHandling.data_loaders;

import strafford_SOM_PKG.straff_RawDataHandling.data_loaders.base.Straff_RawDataLoader;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.Straff_TcTagData;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.base.Straff_BaseRawData;

//stream tcTagdata to build product examples
public class Straff_TcTagDataLoader extends Straff_RawDataLoader{
	public Straff_TcTagDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected Straff_BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		Straff_TcTagData obj = new Straff_TcTagData(msgObj,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//
