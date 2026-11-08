# Marco de Fotos (Forge 1.20.1)

## Items
- **Marco de Fotos**: click derecho en el aire (o Shift + click) abre el menu para elegir imagen/gif/video.
  Click derecho en un bloque lo coloca (como una pintura, en pared, piso o techo). Click derecho sobre un marco ya
  colocado abre el menu para cambiar su imagen.
- **Imagen**: click derecho abre el menu. Con "Mostrar" la imagen aparece en la pantalla de TODOS los jugadores.

## Importar
- Boton "Examinar...", ruta escrita, enlace http(s) o arrastrar el archivo a la ventana.
- PNG, JPG, BMP, GIF. Video (mp4, webm, mkv, mov, avi...) se convierte a GIF con **ffmpeg** (sin audio, max 12 s).
  ffmpeg debe estar instalado en el PATH o su ruta en `config/marcofotos-client.toml`.
- Limite: 8 MB por archivo.

## Configuracion del item Imagen
Pestanas: Animacion (entrada, salida, suavizado, duraciones, con vista previa), Reproduccion (duracion, velocidad,
reversa, loop, ida y vuelta), Aspecto (opacidad, tamano, rotacion, posicion, espejo).

## Comandos / config
- `/marcofotos ocultar` (op): quita lo que se este mostrando.
- `marcofotos-common.toml`: `soloOperadoresMuestran` (false por defecto).

## Compilar en GitHub
Sube todos los archivos (incluida la carpeta `.github`) a un repositorio. La pestana **Actions** compila sola y
publica el `.jar` en **Releases**.
