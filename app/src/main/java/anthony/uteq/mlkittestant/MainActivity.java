package anthony.uteq.mlkittestant;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
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
    private int[] CODES = {100, 1};
    private boolean FilesPermit = true;
    private Uri imageUri = null;
    private ImageView img;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //tratar de obtener permisos para archivos
        requestPermitStorage();
        //establecer como variable global el contenedor de imagenes
        img = this.findViewById(R.id.imageView);
        //obtener referencia del botón
        Button button = (Button) findViewById(R.id.btnsearchpicture);
        //asignar evento al botón
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(FilesPermit) {
                    //declarar el nuevo intent, el cual se encargará de abrir el visualizador de archivos
                    //para cargar una nueva imagen
                    Intent gallery = new Intent(Intent.ACTION_GET_CONTENT, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                    //ser establecen filtros de archivos que se podran obtener
                    gallery.setType("Image/*");
                    //se ejecuta el intent
                    startActivityForResult(gallery, CODES[0]);
                }
                //startActivity(gallery);
            }
        });
    }

    private void requestPermitStorage(){
        Alerts.MessageToast(MainActivity.this, "No tiene permiso a archivos :c");
        //solicitar acceso a archivos del dispositivo
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, CODES[1]);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //verifica si el resultado de la petición fue satisfactorio
        if(resultCode == Activity.RESULT_OK){
            //verifica si se ha seleccionado una imagen
            if(requestCode == CODES[0]){
                //obtener imageUri
                imageUri = data.getData();
                if(img!=null){
                    try {
                        //ubicar imagen en contenedor ImageView
                        img.setImageURI(imageUri);
                        code();
                    }catch (Exception ex){
                        MyLogs.error("ImgSetUri: " + ex.getMessage());
                    }
                }
            } else if(resultCode == CODES[1]){
                //obtiene respuesta de la imagen
                Alerts.MessageToast(MainActivity.this, "Permiso aceptado");
                FilesPermit = true;
            }
        }
    }

    private void code(){
        InputImage image = null;
        try {
            //obtiene el input imagen a partir de la uri
            image = InputImage.fromFilePath(MainActivity.this, imageUri);
        } catch (IOException e) {
            MyLogs.error("IOimg: "+ e.getMessage());
        }
        //verifica si se obtuvo el InputImage
        if(image!=null){
            // código del algoritmo
            ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
            labeler.process(image)
                    .addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
                        @Override
                        public void onSuccess(List<ImageLabel> labels) {
                            MyLogs.info("-------------------------------------");
                            //se declara una lista de objetos, para crear la tabla
                            ArrayList<String[]> lista = new ArrayList<>();
                            //se recorren los resultados obtenidos por el servicio
                            for (ImageLabel label : labels) {
                                int index = label.getIndex();
                                String text = label.getText();
                                float confidence = label.getConfidence();
                                //se agregan los items a la lista
                                lista.add(new String[] {String.valueOf(index), text, String.format("%.5g%n", confidence)});

                                MyLogs.info(index+": "+text + " => IC:"+ confidence);
                            }
                            //se envía la lista de datos a la tabla
                            tableAdapt(lista);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Alerts.MessageToast(MainActivity.this, "MLError: " + e.getMessage());
                        }
                    });
        }
    }

    private void tableAdapt(ArrayList<String[]> lista){
        //obtener la referencia de la tabla en el activity
        TableLayout table = (TableLayout)findViewById(R.id.table);
        //declaramos el objeto que nos creará la tabla dinámica
        TableModel tbModel = new TableModel(MainActivity.this, table);
        //indicamos los encabezados de la tabla
        tbModel.setHeaders(new String[]{"N", "Object", "Accuracy"});
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
}