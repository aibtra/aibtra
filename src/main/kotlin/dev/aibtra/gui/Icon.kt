/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui

import dev.aibtra.main.frame.GuiConfiguration
import java.awt.Image
import java.awt.image.BaseMultiResolutionImage
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.ImageIcon

class Icon private constructor(
	private val imageLight: ImageIcon,
	private val imageDark: ImageIcon
) {
	fun getImageIcon(dark: Boolean): ImageIcon {
		return if (dark) {
			imageDark
		}
		else {
			imageLight
		}
	}

	companion object {
		fun create(imageLight: URL, imageDark: URL): Icon {
			return Icon(createImageIcon(imageLight), createImageIcon(imageDark))
		}

		private fun createImageIcon(url: URL): ImageIcon {
			val image = url.openStream().use {
				ImageIO.read(it)
			}
			val width = image.width
			val height = image.height
			require(width == height)
			require(width % 32 == 0)

			val scaledImages = ArrayList<Image>()
			val fontSize = GuiConfiguration.Fonts.DEFAULT_FONT_SIZE
			if (fontSize < 16) {
				scaledImages.add(image.getScaledInstance(16, 16, Image.SCALE_SMOOTH))
			}
			if (fontSize < 32) {
				scaledImages.add(image.getScaledInstance(32, 32, Image.SCALE_SMOOTH))
			}
			if (fontSize < 48) {
				scaledImages.add(image.getScaledInstance(48, 48, Image.SCALE_SMOOTH))
			}
			if (fontSize < 64) {
				scaledImages.add(image.getScaledInstance(64, 64, Image.SCALE_SMOOTH))
			}

			val baseMultiResolutionImage = BaseMultiResolutionImage(*scaledImages.toTypedArray())
			return ImageIcon(baseMultiResolutionImage)
		}
	}
}