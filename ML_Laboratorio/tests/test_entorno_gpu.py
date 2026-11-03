import unittest

from herramientas.adquirir_dentex import LAB
from herramientas.verificar_entorno_gpu import package_version


class GpuEnvironmentTests(unittest.TestCase):
    def test_phase_c_files_and_package_version_lookup(self):
        self.assertTrue((LAB / "FICHA_MODELO_Y_LICENCIAS_2026-10-28.md").is_file())
        self.assertTrue((LAB / "requirements-entrenamiento.txt").is_file())
        self.assertIsNotNone(package_version("Pillow"))
        self.assertIsNone(package_version("nonexistent-coronyx-package"))


if __name__ == "__main__":
    unittest.main()

