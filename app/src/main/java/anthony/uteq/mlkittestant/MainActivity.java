package anthony.uteq.mlkittestant;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import anthony.uteq.mlkittestant.utiles.Alerts;
import anthony.uteq.mlkittestant.utiles.MyLogs;
import anthony.uteq.mlkittestant.utiles.TableModel;

public class MainActivity extends AppCompatActivity {

    //0: respuesta a carga de imagen
    //1: respuesta solicitud de permiso a archivos
    private int[] CODES = {50, 100, 200, 250};
    private boolean FilesPermit = true;
    private ImageView img;
    ArrayList<String[]> lista;

    private Translator EnglishToSpanish = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //tratar de obtener permisos para archivos y la camara
        requestPermitStorage();
        //Inicia la descarga de los paquetes para traducir
        initTraslate();

        //establecer como variable global el contenedor de imagenes
        img = this.findViewById(R.id.imageView);
        //obtener referencia del botón
        Button button = (Button) findViewById(R.id.btnsearchpicture);
        //asignar evento al botón
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (FilesPermit) {
                    //declarar el nuevo intent, el cual se encargará de abrir el visualizador de archivos
                    //para cargar una nueva imagen
                    Intent gallery = new Intent(Intent.ACTION_GET_CONTENT, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                    //ser establecen filtros de archivos que se podran obtener
                    gallery.setType("Image/*");
                    //se ejecuta el intent
                    startActivityForResult(gallery, CODES[0]);
                } else {
                    Alerts.MessageToast(MainActivity.this, "no tienes permiso");
                }
                //startActivity(gallery);
            }
        });
        Button buttonT = (Button) findViewById(R.id.btntakepicture);
        buttonT.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    Intent takePic = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    if (takePic.resolveActivity(getPackageManager()) != null) {
                        startActivityForResult(takePic, CODES[2]);
                    }else {
                        Alerts.MessageToast(MainActivity.this, "mal intent");
                    }
                } else {
                    Alerts.MessageToast(MainActivity.this, "no tienes permiso");
                }
            }
        });
    }

    private void requestPermitStorage() {
        Alerts.MessageToast(MainActivity.this, "No tiene permiso a archivos :c");
        //solicitar acceso a archivos del dispositivo
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA
                }, CODES[1]);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //verifica si el resultado de la petición fue satisfactorio
        if (resultCode == Activity.RESULT_OK) {
            //verifica si se ha seleccionado una imagen
            if (requestCode == CODES[0]) {
                //obtener imageUri
                Uri imageUri = data.getData();
                if (img != null) {
                    try {
                        //ubicar imagen en contenedor ImageView
                        img.setImageURI(imageUri);
                        InputImage image = null;
                        try {
                            //obtiene el input imagen a partir de la uri
                            image = InputImage.fromFilePath(MainActivity.this, imageUri);
                        } catch (IOException e) {
                            MyLogs.error("IOimg: " + e.getMessage());
                        }
                        identifyLabels(image);
                    } catch (Exception ex) {
                        MyLogs.error("ImgSetUri: " + ex.getMessage());
                    }
                }
            } else if (requestCode == CODES[1]) {
                //obtiene respuesta de la imagen
                Alerts.MessageToast(MainActivity.this, "Permiso aceptado");
                FilesPermit = true;
            } else if (requestCode == CODES[2]) {
                //obtiene respuesta de la imagen
                Bundle extras = data.getExtras();
                Bitmap imgBitMap = (Bitmap) extras.get("data");
                if (img != null) {
                    InputImage image = null;
                    try {
                        //ubicar imagen en contenedor ImageView
                        img.setImageBitmap(imgBitMap);
                        //obtiene el input imagen a partir de un bitMap
                        image = InputImage.fromBitmap(imgBitMap, 0);
                    } catch (Exception ex) {
                        MyLogs.error("ImgSetUri: " + ex.getMessage());
                    }
                    identifyLabels(image);
                }
            }
        }
        MyLogs.error("resultCode: " + resultCode);
    }

    /*******************************************************************************************
     *                                  Etiquetado de imágenes                                 *
     *******************************************************************************************/

    private void identifyLabels(InputImage image) {

        //verifica si se obtuvo el InputImage
        if (image != null) {
            // código del algoritmo
            ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
            labeler.process(image)
                    .addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
                        @Override
                        public void onSuccess(List<ImageLabel> labels) {
                            MyLogs.info("-------------------------------------");
                            //se declara una lista de objetos, para crear la tabla
                            lista = new ArrayList<>();
                            boolean flagTraslate = EnglishToSpanish != null;
                            //se recorren los resultados obtenidos por el servicio
                            for (ImageLabel label : labels) {
                                int index = label.getIndex();
                                String text = label.getText();
                                float confidence = label.getConfidence();
                                //se agregan los items a la lista
                                if (flagTraslate) {
                                    lista.add(new String[]{String.valueOf(index), text, "", String.format("%.5g%n", confidence)});
                                    traslateList(text, lista.size() - 1);
                                } else {
                                    lista.add(new String[]{String.valueOf(index), text, String.format("%.5g%n", confidence)});
                                }
                                MyLogs.info(index + ": " + text + " => IC:" + confidence);
                            }
                            if (!flagTraslate) {
                                tableAdapt(lista, flagTraslate);
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Alerts.MessageToast(MainActivity.this, "MLError: " + e.getMessage());
                        }
                    });
        }else{
            Alerts.MessageToast(MainActivity.this, "Imagen no disponible");
        }
    }

    private void tableAdapt(ArrayList<String[]> lista, boolean isTraslateAvalible) {
        //obtener la referencia de la tabla en el activity
        TableLayout table = (TableLayout) findViewById(R.id.table);
        //declaramos el objeto que nos creará la tabla dinámica
        TableModel tbModel = new TableModel(MainActivity.this, table);
        //indicamos los encabezados de la tabla
        if (isTraslateAvalible) {
            tbModel.setHeaders(new String[]{"N", "Object", "Traslated", "Accuracy"});
        } else {
            tbModel.setHeaders(new String[]{"N", "Object", "Accuracy"});
        }
        //enviamos los datos del cuerpo de la tabla
        tbModel.setRows(lista);
        //configuramos la tabla, colores del encabezado y el cuerpo
        // tanto del texto como el fondo
        tbModel.setHeaderBackGroundColor(R.color.back_black);
        tbModel.setRowsBackGroundColor(R.color.back_white);

        tbModel.setHeadersForeGroundColor(R.color.back_white);
        tbModel.setRowsForeGroundColor(R.color.back_black);
        //Modifica la tabla a partir de los datos enviados y los parámetros enviados
        tbModel.makeTable();

        MyLogs.info(" FIN ");
    }

    /*******************************************************************************************
     *                              Traducción en el dispositivo                               *
     *******************************************************************************************/

    private void initTraslate() {
        // gif de carga
        Alerts.LoadingDialog(MainActivity.this);
        Alerts.showLoading();
        //descarga
        TranslatorOptions options =
                new TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.ENGLISH)
                        .setTargetLanguage(TranslateLanguage.SPANISH)
                        .build();
        this.EnglishToSpanish = Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();
        this.EnglishToSpanish.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void v) {
                                Alerts.closeLoading();
                                Alerts.MessageToast(MainActivity.this, "Paquete Descargado");
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                Alerts.closeLoading();
                                Alerts.MessageToast(MainActivity.this, "ErrPackTras: " + e.getMessage());
                            }
                        });
    }

    private void traslateList(String text, int index) {
        if (this.EnglishToSpanish != null) {

            this.EnglishToSpanish.translate(text)
                    .addOnSuccessListener(
                            new OnSuccessListener<String>() {
                                @Override
                                public void onSuccess(String translatedText) {
                                    //verifica la lista de elementos y que el indice buscado esté
                                    //entre los límites de la lista
                                    if (lista != null) {
                                        if (index >= 0 && index < lista.size()) {
                                            //obtiene los items
                                            String[] items = lista.get(index);
                                            if (items != null) {
                                                //agrega la traducción al vector
                                                items[2] = translatedText;
                                            }
                                        }
                                        if (lista.size() - 1 == index) {
                                            //se envía la lista de datos a la tabla
                                            tableAdapt(lista, true);
                                        }
                                    }
                                }
                            })
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(Exception e) {
                                    Alerts.MessageToast(MainActivity.this, "ErrTrasWord: " + e.getMessage());
                                    if (lista.size() - 1 == index) {
                                        //se envía la lista de datos a la tabla
                                        tableAdapt(lista, true);
                                    }
                                }
                            });
        } else {
            Alerts.MessageToast(MainActivity.this, "Traductor no disponible");
        }
    }
}