#ifndef _Client_GoogleMaps_h_
#define _Client_GoogleMaps_h_

#include <CtrlLib/CtrlLib.h>
using namespace Upp;

#define LAYOUTFILE <MapCtrl/GoogleMaps.lay>
#include <CtrlCore/lay.h>

#define IMAGECLASS GoogleMapsImg
#define IMAGEFILE <MapCtrl/GoogleMaps.iml>
#include <Draw/iml_header.h>

void   SetGoogleMapsKey(const char *key);
String GetGoogleMap(double center_x, double center_y, int zoom, int cx, int cy,
                    const char *format = "png", String *error = NULL);
Image  GetGoogleMapImage(double center_x, double center_y, int zoom, int cx, int cy,
                         const char *format = "png", String *error = NULL);
double CvDeg(double deg, double minutes, double seconds);

Pointf GoogleMapsPixelToGps(Pointf center, int zoom, Point diff);
Pointf GoogleMapsPixelToGps(Pointf center, int zoom, Size sz, Point p);
Pointf GoogleMapsGpsToPixelDiff(Pointf center, int zoom, Pointf gps);
Pointf GoogleMapsGpsToPixel(Pointf center, int zoom, Size sz, Pointf gps);

Pointf ScanGPS(const char *s);

String FormatGPSX(double x);
String FormatGPSY(double y);
String FormatGPS(Pointf p);


struct MapImage : public Ctrl {
	Image  map;
	String error;
	Point  home;
	Vector<Pointf> persons;
	Vector<Image> images;
	Image overlay;
	
	Callback1<Point> WhenLeftClick;
	
	virtual void LeftDown(Point p, dword)
	{
		WhenLeftClick(p);
	}
	
	virtual void Paint(Draw& w) {
		Size sz = GetSize();
		w.DrawRect(sz, SColorPaper());
		if(IsNull(map))
			w.DrawText(0, 0, error);
		else {
			w.DrawImage(0, 0, map);
			for(int i = 0; i < persons.GetCount(); i++) {
				Pointf person = persons[i];
				Image& src_img = images[i];
				if (src_img.GetSize() == Size(0,0)) continue;
				Image img = CachedRescale(src_img, Size(16,16));
				Point p = img.GetSize() / 2;
				w.DrawImage(person.x - p.x, person.y - p.y, img);
			}
			Point p = GoogleMapsImg::Pin().GetHotSpot();
			w.DrawImage(home.x - p.x, home.y - p.y, GoogleMapsImg::Pin());
		}
		if (!overlay.IsEmpty())
			w.DrawImage(0, 0, overlay);
	}
	
	MapImage() { SetFrame(ViewFrame()); BackPaint(); }
};

struct MapDlgDlg : public WithMapDlgLayout<TopWindow> {
	typedef MapDlgDlg CLASSNAME;

	Pointf   home;
	Pointf   center;
	MapImage map;
	
	void LoadMap();
	void SetHome();
	void MapClick(Point p);
	
	void ZoomIn();
	void ZoomOut();
	void Move(int x, int y);
	
	void   Set(Pointf p);
	Pointf Get() { return home; }
	
	void SetPersonCount(int i) {map.persons.SetCount(i); map.images.SetCount(i);}
	void SetPerson(int i, Pointf coord, Image img) {map.persons[i] = GoogleMapsGpsToPixel(center, ~zoom, Size(640, 640), coord); map.images[i] = img;}
	
	MapDlgDlg();
	
	Callback1<Pointf> WhenHome;
	Callback WhenMove;
	
};


#endif
