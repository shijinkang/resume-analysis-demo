# -*- coding: utf-8 -*-
import codecs

with codecs.open('d:/vibeCoding/resume-analysis-demo/frontend/index.html', 'r', 'utf-8') as f:
    lines = f.readlines()

step0_start = None
step0_end = None

for i, line in enumerate(lines):
    if '<!-- ===== STEP 0: Three-Column Layout =====' in line:
        step0_start = i
    if '<!-- END STEP 1 -->' in line and step0_start is not None:
        step0_end = i
        break

print('STEP0 start line:', step0_start + 1)
print('END STEP 1 line:', step0_end + 1)

new_step0 = """    <!-- ===== STEP 0: Three-Column Layout ===== -->
    <div x-show="currentStep === 0" x-transition:enter="transition ease-out duration-200"
         x-transition:enter-start="opacity-0 translate-y-2" x-transition:enter-end="opacity-100 translate-y-0">

      <div class="text-center mb-8">
        <h2 class="text-2xl font-bold text-gray-900">\\u667a\\u80fd\\u7b80\\u5386\\u5206\\u6790\\u7cfb\\u7edf</h2>
        <p class="text-gray-500 mt-1.5 text-sm">\\u9009\\u62e9\\u6216\\u4e0a\\u4f20 JD \\u548c\\u7b80\\u5386\\uff0c\\u5f00\\u59cb AI \\u667a\\u80fd\\u5339\\u914d\\u5206\\u6790</p>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        COLUMNS_PLACEHOLDER
      </div>
    </div>
    <!-- END STEP 0 -->
"""

print('Script loaded - replacing section lines', step0_start+1, 'to', step0_end+1)
