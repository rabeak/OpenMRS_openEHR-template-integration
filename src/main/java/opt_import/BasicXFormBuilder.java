package opt_import;

import java.io.File;

import nu.xom.*;

public class BasicXFormBuilder {
	
	/*retrieves the basic structure including mandatory elements for OpenMRS XForms and Layout XMLs*/
	protected Document createBasicXForm()
	{
		try {
			  Builder parser = new Builder();
			  File xmlFile=new File("src/main/resources/BasicForm.xml");
			  Document doc = parser.build(xmlFile);
			  return doc;
			}
			catch (Exception e) {
				e.printStackTrace();
				return null;
			}	
	}
	
	protected Document createBasicLayout()
	{
		try {
			  Builder parser = new Builder();
			  File xmlFile=new File("src/main/resources/BasicLayout.xml");
			  Document doc = parser.build(xmlFile);
			  return doc;
			}
			catch (Exception e) {
				e.printStackTrace();
				return null;
			}	
		
	}
	


}
