package com.avant.api

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.S3Object
import kotlinx.coroutines.experimental.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.http.ResponseEntity
import org.springframework.util.FileCopyUtils
import org.springframework.web.bind.annotation.*

import javax.imageio.ImageIO
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.Part
import java.awt.*
import java.awt.image.BufferedImage
import java.io.*
import java.util.ArrayList
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct


@RestController
@CrossOrigin
@RequestMapping("/file")
class FileAPI {
	
	private val bucketName = "avant-html-1"
	private val amazonServer = "https://s3-eu-west-1.amazonaws.com/"
	
	@Autowired
	private lateinit var appcontext: ConfigurableApplicationContext
	@Autowired
	private lateinit var environment: ConfigurableEnvironment
	
	
	private val filePlace = System.getProperty("user.dir") + "/src/main/resources/files/"
	private val keys = ConcurrentHashMap<String, List<String>>()
	private var s3Client: AmazonS3? = null
	
	@PostConstruct
	fun postConstruct() {
		launch {
			s3Client = AmazonS3Client.builder().withCredentials(
					ProfileCredentialsProvider(environment.getProperty("amazon.s3.key"), environment.getProperty("amazon.s3.secret"))
			).build()
		}
		
		val parent = File(filePlace)
		if (parent.exists()) {
			parent.delete()
		}
		parent.mkdirs()
	}
	
	@RequestMapping("/get")
	fun getFile(response: HttpServletResponse, @RequestParam("file") filePath: String) {
		response.sendRedirect(amazonServer + bucketName + "/" + filePath)
	}
	
	@RequestMapping("/getimg/{size}")
	@Throws(Exception::class)
	fun getimg(@PathVariable("size") size: Int,
	           @RequestParam(value = "img", required = false) filePath: String,
	           response: HttpServletResponse, request: HttpServletRequest) {
		
		if (s3Client!!.doesObjectExist(bucketName, "resized/" + size + filePath)) {
			response.sendRedirect(amazonServer + bucketName + "/resized/" + size + filePath)
			return
		}
		
		var s3Object: S3Object? = null
		println("creating resized version of $filePath ($size)")
		val oldFile = File(filePlace + filePath)
		val newFile = File(System.getProperty("user.dir") + "/src/main/resources/files/" + filePath)
		try {
			oldFile.createNewFile()
			
			try {
				s3Object = s3Client!!.getObject(GetObjectRequest(bucketName, filePath))
				FileCopyUtils.copy(
						s3Object!!.objectContent,
						FileOutputStream(oldFile)
				)
			} finally {
				s3Object!!.close()
			}
			
			// <!-- img resize down  -->
			
			synchronized(this) {
				
				val originalImage = ImageIO.read(oldFile)
				val h: Int
				val w: Int
				if (Math.max(originalImage.height, originalImage.width) <= size) {
					getFile(response, filePath)
					return
				}
				if (originalImage.height > originalImage.width) {
					w = size
					h = (originalImage.height * (w * 1.0 / originalImage.width)).toInt()
				} else {
					h = size
					w = (originalImage.width * (h * 1.0 / originalImage.height)).toInt()
				}
				val image = originalImage.getScaledInstance(w, h, Image.SCALE_AREA_AVERAGING)
				val changedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
				val g2d = changedImage.createGraphics()
				g2d.drawImage(image, 0, 0, null)
				g2d.dispose()
				newFile.createNewFile()
				ImageIO.write(changedImage, "jpg", newFile)
				
			}
			
			// <!-- img resize up -->
			
			s3Client!!.putObject(PutObjectRequest(bucketName, "resized/" + size + filePath, newFile))
			
			val sc = request.session.servletContext
			response.contentType = sc.getMimeType(filePath)
			response.setContentLength(newFile.length().toInt())
			response.setHeader("Content-Disposition", "attachment; filename=\"" + filePath + "\"")
			try {
				FileCopyUtils.copy(FileInputStream(newFile), response.outputStream)
			} catch (e: IOException) {
				e.printStackTrace()
			}
			
			newFile.delete()
			oldFile.delete()
			println("done resized version of $filePath ($size)")
			
			
		} catch (e: Exception) {
			e.printStackTrace()
		} finally {
			newFile.delete()
			oldFile.delete()
			System.gc()
			if (s3Object != null) {
				s3Object.close()
			}
		}
		
	}
	//
	//	@Auth(Auth.AuthType.ADMIN)
	//	@RequestMapping(value = "/upload", method = arrayOf(RequestMethod.POST))
	//	@Throws(Exception::class)
	//	fun upload(request: HttpServletRequest, @RequestParam(value = "key", required = false) key: String?): ResponseEntity<*> {
	//		val filePart = request.getPart("file") // Retrieves <input type="file" name="file">
	//		val fileExt = filePart.submittedFileName.substring(filePart.submittedFileName.lastIndexOf("."))
	//		val fileName = UUID.randomUUID().toString() + fileExt
	//		if (key != null) {
	//			(keys as java.util.Map<String, List<String>>).putIfAbsent(key, ArrayList())
	//			keys[key].add(fileName)
	//		}
	//
	//		val inputStream = filePart.inputStream
	//		val path = System.getProperty("user.dir") + "/src/main/resources/files/"
	//		val file = File(path + fileName)
	//		println("Uploading: " + file.absolutePath)
	//		try {
	//			file.createNewFile()
	//		} catch (e: Exception) {
	//			println("Error creating " + file.absolutePath)
	//			file.parentFile.mkdirs()
	//			file.createNewFile()
	//		}
	//
	//		val outputStream = FileOutputStream(file)
	//
	//		var read = 0
	//		val bytes = ByteArray(4096)
	//
	//		while ((read = inputStream.read(bytes)) != -1) {
	//			outputStream.write(bytes, 0, read)
	//		}
	//
	//		outputStream.close()
	//
	//		s3Client!!.putObject(PutObjectRequest("avant-html-1", fileName, file))
	//		file.delete()
	//
	//		return ResponseEntity.ok().body(fileName)
	//	}
	//
	//	@PostMapping("/upload/{key}")
	//	@Throws(Exception::class)
	//	fun uploadKey(request: HttpServletRequest, @PathVariable("key") key: String): ResponseEntity<*> {
	//		return ResponseEntity.ok<ResponseEntity>(upload(request, key))
	//	}
	//
	//	@GetMapping("/findKey/{key}")
	//	@Throws(InterruptedException::class)
	//	fun findKey(@PathVariable("key") key: String): ResponseEntity<*> {
	//		for (i in 0..99) {
	//			if (keys[key] != null) {
	//				return ResponseEntity.ok<List<String>>(keys[key])
	//			}
	//			Thread.sleep(200)
	//		}
	//		return ResponseEntity.status(403).body("{'status':'No key present. Maybe try again later.'}")
	//	}
	//
	//	@Throws(IOException::class)
	//	private fun loadAmazonData(filekey: String) {
	//
	//		val `object` = s3Client!!.getObject(
	//				GetObjectRequest("avant-html-1", filekey))
	//		val objectData = `object`.objectContent
	//
	//		val buf = ByteArray(4096)
	//		var read: Int
	//
	//		while ((read = objectData.read(buf)) > 0) {
	//			if (read == 4096) {
	//				//				fos.write(buf);
	//			} else {
	//				//				fos.write(Arrays.copyOfRange(buf, 0, read));
	//			}
	//		}
	//
	//		objectData.close()
	//		//		fos.close();
	//	}
	//
	//	//TODO Test methods below
	//
	//	@RequestMapping("/dir")
	//	fun dir(@RequestParam(value = "dir", required = false) dir: String?): ResponseEntity<*> {
	//		return if (dir == null) {
	//			ResponseEntity.ok().body(File.listRoots())
	//		} else {
	//			ResponseEntity.ok().body(File(dir).list())
	//		}
	//	}
	//
	//	@GetMapping("/cleanup")
	//	fun cleanup(): ResponseEntity<*> {
	//		for (f in File(filePlace).listFiles()!!) {
	//			try {
	//				f.delete()
	//			} catch (e: Exception) {
	//			}
	//
	//		}
	//		return ResponseEntity.ok("Done")
	//	}
	//
	//	@RequestMapping("/local")
	//	fun local(): ResponseEntity<*> {
	//		return ResponseEntity.ok().body(System.getProperty("user.dir"))
	//	}
	//
	
	
}
