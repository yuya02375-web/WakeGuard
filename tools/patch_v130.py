import runpy

# Stable workflow still calls patch_v130.py; keep that entry point but advance it
# to the v1.3.1 layered patch without touching signing workflow/secrets.
runpy.run_path("tools/patch_v131.py", run_name="__main__")
